/**
 * 小运（后台 AI 运营助手）· 操作确认卡片渲染器
 * ============================================================================
 * 依赖：无（原生 DOM）。样式在同目录 xiaoyun-action.css。
 * 挂载：window.XiaoyunAction
 *
 * ---------------------------------------------------------------------------
 * 【铁律 1】卡片上所有业务文字只能来自 proposal / 响应体，前端一个字都不许自己算
 * ---------------------------------------------------------------------------
 * 历史教训：前端曾经自己把 discount 0.85 渲染成「85 折」、把 threshold 200 渲染成
 * 「满 200」、把两个时间戳相减渲染成「持续 2 天」。一旦服务端口径改了（比如折扣改成
 * 「减 15%」或活动时间改成按自然日算），前端就会和后台列表、和小运的答复、和实际执行
 * 结果三处不一致 —— 运营看到的卡片和真正执行的东西不是一回事，这是最贵的一类 bug。
 *
 * 因此本文件里：
 *   · 展示金额 → 只打印 proposal.fields[].value（服务端已格式化成「¥4.50」）
 *   · 展示状态变化 → 只打印 fields[] 里的字符串
 *   · 展示风险 → 只打印 risk.level / risk.reasons / risk.note
 *   · 展示预检 → 只打印 precheck.checks[].message
 *   · 唯一由前端决定的文字是「界面文案」（按钮名、状态名「待确认/执行中/成功/失败」、
 *     字段名兜底），它们是 chrome，不是业务数据。
 * 验收标准：把网络断开，卡片依然能完整显示 —— 因为它从不发二次请求去补数据。
 *
 * ---------------------------------------------------------------------------
 * 【铁律 2】四态状态机，且执行中必须锁死按钮
 * ---------------------------------------------------------------------------
 *   proposed ──点击确认──▶ running ──code=0────────▶ success
 *      ▲                     │
 *      └──重试（有限）────────┴──code≠0 / 网络异常──▶ failed
 *
 * running 态下：
 *   · 按钮 disabled（不是只改样式），连点产生不了第二个请求
 *   · 同时置 state.busy = true，即使有人用 devtools 去掉 disabled 也会被 JS 挡住
 * 服务端还有幂等键兜底（第二次必然命中 ALREADY_DONE），三层防护缺一不可。
 *
 * ---------------------------------------------------------------------------
 * 【铁律 3】DUPLICATE_ACTION 不是错误
 * ---------------------------------------------------------------------------
 * 服务端对「同一张卡片第二次确认」返回 code=0 + subCode=DUPLICATE_ACTION + data=上次结果。
 * 这是**成功**语义（目标状态已达成），必须渲染成「已完成 + 上次的结果」，
 * 绝不能弹红色报错 —— 否则运营会以为没成功，然后手动去改数据，那才是真事故。
 */
(function (global) {
    'use strict';

    /** 版本号，方便线上排查「用户到底加载了哪版卡片逻辑」。 */
    var VERSION = '1.0.0';

    /** 卡片四态。 */
    var STATE = {
        PROPOSED: 'proposed',   // 待确认
        RUNNING: 'running',     // 执行中
        SUCCESS: 'success',     // 成功
        FAILED: 'failed'        // 失败
    };

    /** 状态 → 界面文案（chrome，非业务数据）。 */
    var STATE_TEXT = {
        proposed: '待确认',
        running: '执行中',
        success: '已完成',
        failed: '未完成'
    };

    /** 风险等级 → 界面文案 + 样式修饰。 */
    var RISK_LEVEL = {
        LOW: ['低风险', 'low'],
        MEDIUM: ['中风险', 'medium'],
        HIGH: ['高风险', 'high']
    };

    /**
     * 已知 subCode → 中文短语。
     * 服务端 message 本来就是给运营看的中文，这里只补一句「一句话结论」，
     * 让运营不用读完整段 message 就知道发生了什么。两者都会显示，不做取舍，
     * 以免服务端文案改了前端反而把人带偏。
     */
    var SUBCODE_TEXT = {
        DUPLICATE_ACTION: '该操作已执行过',
        ACTION_IN_PROGRESS: '另一个请求正在执行',
        PROPOSAL_EXPIRED: '卡片已过期',
        PROPOSAL_MISMATCH: '卡片与实际操作不一致',
        CONFLICT_STOCK_CHANGED: '库存已经变了',
        REFUND_ALREADY_HANDLED: '退款单已经处理过',
        TARGET_NOT_FOUND: '找不到对应的数据',
        PERM_DENIED: '没有该操作权限',
        SELF_APPROVAL_FORBIDDEN: '不能审批自己名下的退款',
        ORDER_STATE_INVALID: '订单当前状态不允许该操作',
        TRACKING_NO_CONFLICT: '运单号已被占用',
        AMBIGUOUS_TARGET: '匹配到多个对象',
        PARAM_MISSING: '缺少必要参数',
        PARAM_INVALID: '参数不合法',
        OPERATOR_MISMATCH: '操作人与卡片不一致',
        CONTEXT_MISSING: '登录信息已失效'
    };

    /**
     * 这些失败「原样重试」有意义（数据变了、并发争抢、网络抖了）。
     * 其余（过期 / 不一致 / 已处理 / 权限）重试必然还是同样的结果，
     * 直接把运营往「让小运重新准备」上引，别让他反复点。
     */
    var RETRYABLE_SUBCODES = {
        CONFLICT_STOCK_CHANGED: 1,
        ACTION_IN_PROGRESS: 1,
        NETWORK_ERROR: 1
    };

    // ========================================================================
    //  DOM 小工具
    // ========================================================================

    /**
     * 建元素。text 一律走 textContent —— 服务端返回的字符串里可能有 < > & 或引号，
     * 用 innerHTML 拼就是给自己埋 XSS；这里全量走文本节点，天然转义。
     */
    function h(tag, className, text) {
        var el = document.createElement(tag);
        if (className) el.className = className;
        if (text !== undefined && text !== null) el.textContent = String(text);
        return el;
    }

    /** 值是否「有内容」（0 和 false 都算有内容，空串/null/undefined/空数组不算）。 */
    function filled(v) {
        if (v === null || v === undefined) return false;
        if (typeof v === 'string') return v.trim() !== '';
        if (Array.isArray(v)) return v.length > 0;
        return true;
    }

    /** 安全取对象路径，任何一层缺失都返回 undefined，绝不抛错（卡片渲染不许炸整页）。 */
    function pick(obj, path) {
        var cur = obj;
        var parts = String(path).split('.');
        for (var i = 0; i < parts.length; i++) {
            if (cur === null || cur === undefined) return undefined;
            cur = cur[parts[i]];
        }
        return cur;
    }

    // ========================================================================
    //  请求通道
    // ========================================================================

    /**
     * 发一次写请求，返回**完整信封** {code, subCode, message, data}。
     *
     * 为什么不直接用 admin.html 的 api()：api() 在 code !== 0 时抛异常、成功时只返回
     * data，把 subCode 吞掉了。而卡片必须区分「成功」「已执行过（DUPLICATE_ACTION）」
     * 「失败」，subCode 是唯一判据，丢了就分不清。
     *
     * 因此这里复用 window.api 的「raw 模式」（T05 在 admin.html 里给 api() 加的第五参），
     * 保证 token 注入、401 登出、403 提示与全站完全一致，不另起一套请求栈。
     */
    function request(path, method, body, headers) {
        var raw = global.api;
        if (typeof raw !== 'function') {
            // 理论上不会发生（admin.html 一定先加载 api()）；真发生了也给出可读原因，
            // 而不是让卡片卡在「执行中」永远转圈。
            return Promise.resolve({
                code: -1,
                subCode: 'NETWORK_ERROR',
                message: '页面脚本未就绪（api 未加载），请刷新页面后重试。',
                data: null
            });
        }
        return Promise.resolve()
            .then(function () {
                // 第五参 raw=true：拿到 {code, subCode, message, data} 原样信封
                return raw(path, method, body, headers, true);
            })
            .catch(function (e) {
                return {
                    code: -1,
                    subCode: 'NETWORK_ERROR',
                    message: (e && e.message) ? e.message : '网络异常，请稍后重试。',
                    data: null
                };
            });
    }

    /** 提案里的 path 是 /api/admin/...；api() 自己会补 /api，所以要削掉这一层。 */
    function stripApiPrefix(p) {
        return String(p || '').replace(/^\/api(?=\/)/, '');
    }

    // ========================================================================
    //  渲染：一张卡片
    // ========================================================================

    /**
     * 渲染一张确认卡片并挂到容器里。
     *
     * @param {Element} container 挂载点（小运消息列表 #aiMessages）
     * @param {Object}  proposal  服务端返回的 ActionProposal
     * @param {Object}  [options] { onSettled: function(proposal, env) }  执行完成回调
     * @return {Element|null} 卡片元素；proposal 结构不可用时返回 null（不抛错）
     */
    function render(container, proposal, options) {
        if (!container || !proposal || typeof proposal !== 'object') return null;
        var confirm = proposal.confirm;
        if (!confirm || !filled(confirm.path)) {
            // 没有 confirm.path 就没有可执行的入口，渲染出来只会误导运营
            if (global.console) global.console.warn('[XiaoyunAction] proposal 缺少 confirm.path，已跳过渲染', proposal);
            return null;
        }

        var opts = options || {};
        var state = {
            current: STATE.PROPOSED,
            busy: false,          // JS 层锁，防 devtools 去掉 disabled 后连点
            secondConfirmed: !pick(proposal, 'risk.requiresSecondConfirm'),
            attempts: 0
        };

        var card = h('div', 'xy-card');
        card.setAttribute('data-state', STATE.PROPOSED);
        card.setAttribute('data-action', proposal.action || '');
        card.setAttribute('data-proposal-id', proposal.proposalId || '');

        // ---------- 头部：动作名 + 风险等级 ----------
        var head = h('div', 'xy-card-head');
        var headMain = h('div', 'xy-head-main');
        headMain.appendChild(h('span', 'xy-title', proposal.actionLabel || proposal.action || '待确认操作'));
        var stChip = h('span', 'xy-state-chip', STATE_TEXT[STATE.PROPOSED]);
        stChip.setAttribute('data-role', 'state-chip');
        headMain.appendChild(stChip);
        head.appendChild(headMain);

        var riskLevel = String(pick(proposal, 'risk.level') || '').toUpperCase();
        if (RISK_LEVEL[riskLevel]) {
            var badge = h('span', 'xy-risk-badge xy-risk-' + RISK_LEVEL[riskLevel][1], RISK_LEVEL[riskLevel][0]);
            head.appendChild(badge);
        }
        card.appendChild(head);

        // ---------- 对象：targetLabel（服务端已拼好「退款单 #14 · DR… · ¥4.50」） ----------
        if (filled(proposal.targetLabel)) {
            card.appendChild(h('div', 'xy-target', proposal.targetLabel));
        }

        // ---------- 字段区：label / value / tone / mono 全部照抄服务端 ----------
        var fields = Array.isArray(proposal.fields) ? proposal.fields : [];
        if (fields.length) {
            var dl = h('dl', 'xy-fields');
            fields.forEach(function (f) {
                if (!f) return;
                var row = h('div', 'xy-field');
                row.appendChild(h('dt', 'xy-field-label', f.label || ''));
                var dd = h('dd', 'xy-field-value', filled(f.value) ? f.value : '-');
                if (f.tone) dd.classList.add('xy-tone-' + String(f.tone).toLowerCase());
                if (f.mono) dd.classList.add('xy-mono');
                row.appendChild(dd);
                dl.appendChild(row);
            });
            card.appendChild(dl);
        }

        // ---------- 预检：只展示服务端给的 message，不翻译、不推断 ----------
        var checks = pick(proposal, 'precheck.checks');
        if (Array.isArray(checks) && checks.length) {
            var pre = h('div', 'xy-precheck');
            pre.appendChild(h('div', 'xy-section-label', '预检'));
            var list = h('ul', 'xy-checks');
            checks.forEach(function (c) {
                if (!c) return;
                var ok = c.ok !== false;
                var li = h('li', 'xy-check ' + (ok ? 'xy-check-ok' : 'xy-check-bad'));
                li.appendChild(h('span', 'xy-check-mark', ok ? '✓' : '✗'));
                li.appendChild(h('span', 'xy-check-text', c.message || c.code || ''));
                list.appendChild(li);
            });
            pre.appendChild(list);
            card.appendChild(pre);
        }

        // ---------- 风险：reasons / note / 不可撤销 / 二次确认 ----------
        var risk = proposal.risk;
        var riskBox = null;
        if (risk && (filled(risk.reasons) || filled(risk.note) || risk.irreversible || risk.requiresSecondConfirm)) {
            riskBox = h('div', 'xy-risk');
            if (Array.isArray(risk.reasons) && risk.reasons.length) {
                var ul = h('ul', 'xy-risk-reasons');
                risk.reasons.forEach(function (r) {
                    ul.appendChild(h('li', null, r));
                });
                riskBox.appendChild(ul);
            }
            if (filled(risk.note)) riskBox.appendChild(h('div', 'xy-risk-note', risk.note));
            if (risk.irreversible) {
                riskBox.appendChild(h('div', 'xy-risk-irreversible', '此操作执行后不可撤销，请核对后再确认。'));
            }
            if (risk.requiresSecondConfirm) {
                var gate = h('label', 'xy-second-confirm');
                var cb = document.createElement('input');
                cb.type = 'checkbox';
                cb.setAttribute('data-role', 'second-confirm');
                cb.addEventListener('change', function () {
                    state.secondConfirmed = cb.checked;
                    syncButtons();
                });
                gate.appendChild(cb);
                gate.appendChild(h('span', null,
                    '我已核对' + (filled(risk.secondConfirmHint) ? '：' + risk.secondConfirmHint : '以上内容')));
                riskBox.appendChild(gate);
            }
            card.appendChild(riskBox);
        }

        // ---------- 结果区（执行后才有内容） ----------
        var resultBox = h('div', 'xy-result');
        resultBox.setAttribute('data-role', 'result');
        resultBox.style.display = 'none';
        card.appendChild(resultBox);

        // ---------- 操作按钮 ----------
        var actions = h('div', 'xy-actions');
        var btnConfirm = h('button', 'xy-btn xy-btn-primary', '确认执行');
        btnConfirm.type = 'button';
        btnConfirm.setAttribute('data-role', 'confirm');
        actions.appendChild(btnConfirm);

        var btnReverse = null;
        if (proposal.reject && filled(proposal.reject.path)) {
            btnReverse = h('button', 'xy-btn xy-btn-ghost', '改为' + (proposal.action && /APPROVE$/.test(proposal.action) ? '拒绝' : '撤销'));
            btnReverse.type = 'button';
            btnReverse.setAttribute('data-role', 'reverse');
            actions.appendChild(btnReverse);
        }
        card.appendChild(actions);

        // ---------- 页脚：过期时间（服务端给的字符串，直接显示） ----------
        if (filled(proposal.expiresAt)) {
            card.appendChild(h('div', 'xy-foot', '此卡片 ' + fmtStamp(proposal.expiresAt) + ' 前有效'));
        }

        // ====================================================================
        //  状态切换
        // ====================================================================

        /** 按钮可用性：三态统一在这里算，避免各处漏判。 */
        function syncButtons() {
            var canClick = state.current === STATE.PROPOSED && !state.busy && state.secondConfirmed;
            btnConfirm.disabled = !canClick;
            if (btnReverse) btnReverse.disabled = !canClick;
            btnConfirm.textContent = state.current === STATE.SUCCESS ? '已执行' : '确认执行';
        }

        /** 切到某个状态，并按状态重建结果区。 */
        function setState(next, payload) {
            state.current = next;
            card.setAttribute('data-state', next);
            stChip.textContent = STATE_TEXT[next] || next;
            stChip.className = 'xy-state-chip xy-state-' + next;
            resultBox.innerHTML = '';
            resultBox.style.display = 'none';

            if (next === STATE.RUNNING) {
                state.busy = true;
                resultBox.style.display = '';
                resultBox.className = 'xy-result xy-result-running';
                resultBox.appendChild(h('span', 'xy-spinner'));
                resultBox.appendChild(h('span', null, '正在执行，请勿重复点击…'));
                btnConfirm.disabled = true;
                if (btnReverse) btnReverse.disabled = true;
                return;
            }

            if (next === STATE.SUCCESS) {
                state.busy = false;
                var duplicate = payload && payload.duplicate;
                resultBox.style.display = '';
                resultBox.className = 'xy-result xy-result-ok' + (duplicate ? ' xy-result-dup' : '');
                // 标题：DUPLICATE_ACTION 用的是「已执行过」，与首次成功区分开（铁律 3）
                resultBox.appendChild(h('div', 'xy-result-title',
                    duplicate ? '该操作已执行过，下面是上次的结果' : '执行成功'));
                if (payload && filled(payload.message) && duplicate) {
                    resultBox.appendChild(h('div', 'xy-result-msg', payload.message));
                }
                var summary = buildSummary(proposal, payload && payload.data);
                if (summary.childNodes.length) resultBox.appendChild(summary);
                syncButtons();
                return;
            }

            if (next === STATE.FAILED) {
                state.busy = false;
                var sub = payload && payload.subCode;
                resultBox.style.display = '';
                resultBox.className = 'xy-result xy-result-bad';
                resultBox.appendChild(h('div', 'xy-result-title',
                    SUBCODE_TEXT[sub] || '执行未完成'));
                if (filled(payload && payload.message)) {
                    resultBox.appendChild(h('div', 'xy-result-msg', payload.message));
                }
                if (filled(sub)) {
                    resultBox.appendChild(h('div', 'xy-result-code', '错误码：' + sub));
                }
                // 能重试的给「重试」，不能重试的明确告诉运营下一步该干什么
                var retryable = RETRYABLE_SUBCODES[sub] === 1;
                var tail = h('div', 'xy-result-actions');
                if (retryable) {
                    var again = h('button', 'xy-btn xy-btn-ghost xy-btn-sm', '重试');
                    again.type = 'button';
                    again.addEventListener('click', function () {
                        if (state.busy) return;
                        state.current = STATE.PROPOSED;
                        setState(STATE.PROPOSED, null);
                        doConfirm();
                    });
                    tail.appendChild(again);
                } else {
                    tail.appendChild(h('span', 'xy-result-hint', '请让小运重新准备这张卡片。'));
                }
                resultBox.appendChild(tail);
                syncButtons();
                return;
            }

            // PROPOSED
            state.busy = false;
            syncButtons();
        }

        // ====================================================================
        //  执行
        // ====================================================================

        /** 确认执行。三层防连点：state.busy（JS）+ disabled（DOM）+ 服务端幂等键。 */
        function doConfirm() {
            if (!(state.current === STATE.PROPOSED) || state.busy) return;
            if (!state.secondConfirmed) return;
            state.attempts += 1;
            setState(STATE.RUNNING, null);

            var headers = Object.assign({}, confirm.requiresHeader || {});
            // 提案 ID 必须带上：服务端靠它找回 PROPOSED 行做「动作/对象/操作人/参数」四项校验，
            // 找不到提案行就会退化成页面入口（只写审计、不做幂等）—— 连点就真的会执行两次。
            if (filled(proposal.proposalId)) headers['X-Proposal-Id'] = proposal.proposalId;

            request(stripApiPrefix(confirm.path), confirm.method || 'POST', confirm.body || null, headers)
                .then(function (env) {
                    if (env && env.code === 0) {
                        var duplicate = env.subCode === 'DUPLICATE_ACTION';
                        setState(STATE.SUCCESS, { duplicate: duplicate, message: env.message, data: env.data });
                    } else {
                        setState(STATE.FAILED, {
                            subCode: env && env.subCode ? env.subCode : 'INTERNAL_ERROR',
                            message: env && env.message ? env.message : '执行失败，请稍后重试。'
                        });
                    }
                    if (typeof opts.onSettled === 'function') opts.onSettled(proposal, env);
                })
                .catch(function (e) {
                    // request() 已经把异常转成信封，走到这里说明是渲染/回调里的意外错误，
                    // 仍然必须把卡片从「执行中」释放出来，否则运营会一直等。
                    setState(STATE.FAILED, { subCode: 'INTERNAL_ERROR', message: String(e && e.message || e) });
                    if (typeof opts.onSettled === 'function') opts.onSettled(proposal, null);
                });
        }

        /**
         * 反向操作（退款卡片上的「改为拒绝」）。
         *
         * 注意**故意不带 X-Proposal-Id**：提案行的 action 是 REFUND_APPROVE，
         * 而这里要执行 REFUND_REJECT，服务端的 verifyProposal 会因为「动作对不上」
         * 直接 400 PROPOSAL_MISMATCH。不带提案 ID 时服务端按「直接调接口」处理
         * （entry=API）：幂等键照样生效、审计照样落库，只是不认这张卡片。
         * 带 Idempotency-Key 已足够防连点。
         */
        function doReverse() {
            if (!(state.current === STATE.PROPOSED) || state.busy) return;
            var rev = proposal.reject;
            if (!rev || !filled(rev.path)) return;
            state.attempts += 1;
            setState(STATE.RUNNING, null);

            var headers = Object.assign({}, rev.requiresHeader || {});
            delete headers['X-Proposal-Id'];

            request(stripApiPrefix(rev.path), rev.method || 'POST', rev.body || null, headers)
                .then(function (env) {
                    if (env && env.code === 0) {
                        setState(STATE.SUCCESS, {
                            duplicate: env.subCode === 'DUPLICATE_ACTION',
                            message: env.message,
                            data: env.data
                        });
                    } else {
                        setState(STATE.FAILED, {
                            subCode: env && env.subCode ? env.subCode : 'INTERNAL_ERROR',
                            message: env && env.message ? env.message : '执行失败，请稍后重试。'
                        });
                    }
                    if (typeof opts.onSettled === 'function') opts.onSettled(proposal, env);
                })
                .catch(function (e) {
                    setState(STATE.FAILED, { subCode: 'INTERNAL_ERROR', message: String(e && e.message || e) });
                });
        }

        btnConfirm.addEventListener('click', doConfirm);
        if (btnReverse) btnReverse.addEventListener('click', doReverse);

        syncButtons();
        container.appendChild(card);
        return card;
    }

    // ========================================================================
    //  结果摘要（只读服务端给的字段，不做任何换算）
    // ========================================================================

    /**
     * 把执行结果里有意义的字段挑出来展示。
     *
     * 只认「本身就是给人看的字符串」或「原样数字」的键，且键名 → 中文标题的映射是
     * 固定的界面文案。这里不做任何格式化（不 toFixed、不算百分比、不拼时间差），
     * 因为一旦前端开始「加工」，口径漂移就回来了。
     */
    var SUMMARY_KEYS = [
        ['statusLabel', '当前状态'],
        ['previousStatusLabel', '原状态'],
        ['orderNo', '订单号'],
        ['trackingNo', '运单号'],
        ['company', '快递公司'],
        ['stock', '当前库存'],
        ['amount', '金额'],
        ['name', '名称'],
        // 风险消息一键已读的结果（服务端原样返回的整数，前端不做任何换算）
        ['affected', '已读条数'],
        ['remainingUnread', '剩余未读'],
        ['id', '编号']
    ];

    function buildSummary(proposal, data) {
        var box = h('dl', 'xy-result-fields');
        if (!data || typeof data !== 'object') {
            // 没有结果体：至少让运营知道作用在哪个对象上（来自 proposal，不是猜的）
            if (filled(proposal.targetLabel)) {
                var r = h('div', 'xy-field');
                r.appendChild(h('dt', 'xy-field-label', '操作对象'));
                r.appendChild(h('dd', 'xy-field-value', proposal.targetLabel));
                box.appendChild(r);
            }
            return box;
        }
        SUMMARY_KEYS.forEach(function (pair) {
            var key = pair[0];
            var label = pair[1];
            var v = data[key];
            // 跳过 null/undefined/空串；0 和 false 是有效值，必须显示
            if (v === null || v === undefined || v === '') return;
            if (typeof v === 'object') return;
            if (key === 'id' && !filled(proposal.targetLabel)) {
                // 只有拿不到 targetLabel 时才退回显示裸 ID，否则重复
                box.appendChild(fieldRow(label, v, false));
                return;
            }
            if (key === 'id') return;
            box.appendChild(fieldRow(label, v, key === 'orderNo' || key === 'trackingNo'));
        });
        return box;
    }

    function fieldRow(label, value, mono) {
        var row = h('div', 'xy-field');
        row.appendChild(h('dt', 'xy-field-label', label));
        var dd = h('dd', 'xy-field-value', value);
        if (mono) dd.classList.add('xy-mono');
        row.appendChild(dd);
        return row;
    }

    // ========================================================================
    //  渲染：一批卡片 / 追问选项
    // ========================================================================

    /**
     * 批量渲染（小运一轮回了多张卡片时）。
     *
     * @return {Array<Element>} 成功渲染的卡片
     */
    function renderAll(container, proposals, options) {
        var out = [];
        if (!container || !Array.isArray(proposals)) return out;
        proposals.forEach(function (p) {
            var el = render(container, p, options);
            if (el) out.push(el);
        });
        return out;
    }

    /**
     * 渲染小运的追问选项（needsClarification.options），点一下填进输入框。
     *
     * 兼容两种后端形态：
     *   · needsClarification === true            → 只把输入框聚焦，不画选项
     *   · needsClarification === {options:[...]} → 画可点选项
     * 这样后端无论先上线哪种形态，前端都不会炸。
     *
     * @param {Element}  container 挂载点
     * @param {Array}    options   字符串数组
     * @param {Function} onPick    function(text)
     * @return {Element|null}
     */
    function renderOptions(container, options, onPick) {
        if (!container || !Array.isArray(options) || !options.length) return null;
        var wrap = h('div', 'xy-options');
        wrap.appendChild(h('div', 'xy-options-label', '小运需要你补充：'));
        var bar = h('div', 'xy-options-bar');
        options.forEach(function (opt) {
            if (!filled(opt)) return;
            var b = h('button', 'xy-option', String(opt));
            b.type = 'button';
            b.addEventListener('click', function () {
                if (typeof onPick === 'function') onPick(String(opt));
            });
            bar.appendChild(b);
        });
        wrap.appendChild(bar);
        container.appendChild(wrap);
        return wrap;
    }

    /**
     * 把 ISO 时间串显示成「10-22 15:03」。
     * 这是**界面文案**（chrome），不是业务数据；原文在 tooltip 里保留完整值，
     * 万一前端截断规则和服务端不一致，运营还能看到原文。
     */
    function fmtStamp(iso) {
        var s = String(iso || '');
        var m = s.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
        if (!m) return s;
        return m[2] + '-' + m[3] + ' ' + m[4] + ':' + m[5];
    }

    // ========================================================================
    //  导出
    // ========================================================================

    global.XiaoyunAction = {
        version: VERSION,
        STATE: STATE,
        render: render,
        renderAll: renderAll,
        renderOptions: renderOptions
    };
})(window);
