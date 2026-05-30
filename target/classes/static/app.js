// Antigravity Notification Core Dashboard Controller

let notificationsMap = new Map(); // Keep track of notifications by ID

document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

function initApp() {
    // Generate initial idempotency key
    rotateIdempotencyKey();

    // Attach Event Listeners
    document.getElementById('btnGenKey').addEventListener('click', rotateIdempotencyKey);
    document.getElementById('notificationForm').addEventListener('submit', handleFormSubmit);
    document.getElementById('outageToggle').addEventListener('change', handleOutageToggle);
    document.getElementById('btnCloseModal').addEventListener('click', closeModal);
    window.addEventListener('click', (e) => {
        if (e.target === document.getElementById('detailsModal')) {
            closeModal();
        }
    });

    // Dynamic Channel Labels
    const channelRadios = document.querySelectorAll('input[name="channel"]');
    channelRadios.forEach(radio => {
        radio.addEventListener('change', handleChannelChange);
    });

    // Fetch initial database history
    fetchHistory();

    // Establish real-time Event Stream
    connectEventStream();
}

// Generate secure UUIDv4 for Idempotency
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

function rotateIdempotencyKey() {
    document.getElementById('idempotencyKey').value = generateUUID();
}

// Dynamically adjust inputs depending on the channel selection
function handleChannelChange(e) {
    const channel = e.target.value;
    const recipientLabel = document.getElementById('lblRecipient');
    const recipientInput = document.getElementById('recipient');

    if (channel === 'EMAIL') {
        recipientLabel.textContent = 'Recipient Email';
        recipientInput.placeholder = 'user@example.com';
        recipientInput.type = 'email';
    } else if (channel === 'SMS') {
        recipientLabel.textContent = 'Recipient Phone';
        recipientInput.placeholder = '+919988776655';
        recipientInput.type = 'tel';
    } else if (channel === 'IN_APP') {
        recipientLabel.textContent = 'Recipient User Token';
        recipientInput.placeholder = 'usr_token_99a8b7';
        recipientInput.type = 'text';
    }
}

// Fetch historical records from database
async function fetchHistory() {
    try {
        const response = await fetch('/api/v1/notifications');
        if (response.ok) {
            const notifications = await response.json();
            notifications.forEach(n => {
                notificationsMap.set(n.id, n);
            });
            renderHistoryTable();
            updateMetrics();
            appendLog('[System] Fetched existing notification records from H2 database.', 'system-line');
        }
    } catch (e) {
        appendLog(`[System] Error fetching outbox history: ${e.message}`, 'error-line');
    }
}

// Connect to Server-Sent Events updates
function connectEventStream() {
    const eventSource = new EventSource('/api/v1/notifications/stream');

    eventSource.addEventListener('connection', (e) => {
        appendLog(`[SSE] ${e.data}`, 'system-line');
    });

    eventSource.addEventListener('notification-update', (e) => {
        const notification = JSON.parse(e.data);
        const oldState = notificationsMap.get(notification.id);
        notificationsMap.set(notification.id, notification);
        
        // Log state changes in the terminal
        logStateTransition(notification, oldState);
        
        // Update table row and counters
        upsertTableRow(notification);
        updateMetrics();
    });

    eventSource.onerror = (err) => {
        appendLog('[SSE] Connection lost. Retrying in 3 seconds...', 'error-line');
        eventSource.close();
        setTimeout(connectEventStream, 3000);
    };
}

// Log status changes inside terminal
function logStateTransition(n, old) {
    const channelIcon = getChannelIcon(n.channel);
    let logClass = 'info-line';
    let msg = '';

    if (!old) {
        msg = `[Created] Notification #${n.id} (${n.channel}) enqueued in Outbox table. Status: PENDING`;
    } else if (old.status !== n.status) {
        msg = `[Transition] Notification #${n.id}: ${old.status} -> ${n.status}`;
        
        if (n.status === 'PROCESSING') {
            logClass = 'info-line';
            msg += ` (Processing worker picked up dispatch)`;
        } else if (n.status === 'SUCCESS') {
            logClass = 'success-line';
            msg += ` (Successfully delivered on attempt ${n.attempts})`;
        } else if (n.status === 'FAILED') {
            logClass = 'error-line';
            msg += ` (Hard Failure after ${n.attempts} attempts: ${n.lastError})`;
        } else if (n.status === 'PENDING') {
            logClass = 'retry-line';
            msg += ` (Transient error. RetryScheduled: attempt ${n.attempts} finished. Next retry: ${formatTime(n.nextRetryAt)})`;
        }
    } else if (n.attempts > old.attempts && n.status === 'PENDING') {
        msg = `[Retry Engine] Notification #${n.id} failed attempt ${n.attempts}. Re-scheduling with backoff. Next: ${formatTime(n.nextRetryAt)}`;
        logClass = 'retry-line';
    } else {
        return; // nothing meaningful to log
    }

    appendLog(`${channelIcon} ${msg}`, logClass);
}

// Handle Dispatch Submission
async function handleFormSubmit(e) {
    e.preventDefault();

    const idempotencyKey = document.getElementById('idempotencyKey').value;
    const channel = document.querySelector('input[name="channel"]:checked').value;
    const recipient = document.getElementById('recipient').value;
    const title = document.getElementById('title').value;
    const content = document.getElementById('content').value;

    const payload = { channel, recipient, title, content };
    const btnSubmit = document.getElementById('btnSubmit');
    
    btnSubmit.disabled = true;
    btnSubmit.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Dispatching...';

    appendLog(`[API] Dispatching POST /api/v1/notifications (Key: ${idempotencyKey.substring(0,8)}...)`, 'system-line');

    try {
        const response = await fetch('/api/v1/notifications', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (response.status === 201) {
            appendLog(`[API] 201 Created. Notification ID #${data.id} registered.`, 'success-line');
            if (document.getElementById('chkAutoKey').checked) {
                rotateIdempotencyKey();
            }
        } else if (response.status === 200) {
            appendLog(`[Idempotency] 200 OK (CACHED). Key already processed! Returned Cached Notification ID #${data.id}.`, 'success-line');
        } else if (response.status === 409) {
            appendLog(`[Idempotency] 409 Conflict! Key is currently active and processing. Request ignored.`, 'error-line');
        } else {
            appendLog(`[API] Error ${response.status}: ${data.message || 'Unknown error'}`, 'error-line');
        }

    } catch (err) {
        appendLog(`[API] Connection Failure: ${err.message}`, 'error-line');
    } finally {
        btnSubmit.disabled = false;
        btnSubmit.innerHTML = '<i class="fa-solid fa-paper-plane"></i> Dispatch Asynchronously';
    }
}

// Handle Provider Outage Toggle Switch
async function handleOutageToggle(e) {
    const checked = e.target.checked;
    const banner = document.getElementById('outageBanner');
    
    try {
        const response = await fetch('/api/v1/notifications/simulate-outage', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ simulate: checked })
        });

        if (response.ok) {
            if (checked) {
                banner.classList.remove('hidden');
                appendLog('[Sandbox] OUTAGE SIMULATION INJECTED! Third-party providers are offline.', 'error-line');
            } else {
                banner.classList.add('hidden');
                appendLog('[Sandbox] Outage simulated resolved. Providers are online.', 'success-line');
            }
        }
    } catch (err) {
        appendLog(`[Sandbox] Error toggling outage simulation: ${err.message}`, 'error-line');
        e.target.checked = !checked; // revert
    }
}

// Render outbox table
function renderHistoryTable() {
    const tbody = document.getElementById('historyTableBody');
    tbody.innerHTML = '';

    if (notificationsMap.size === 0) {
        tbody.innerHTML = `
            <tr id="noDataRow">
                <td colspan="6" class="no-data">No notifications sent yet. Use the dispatch tool above!</td>
            </tr>`;
        return;
    }

    // Sort by ID descending
    const sorted = Array.from(notificationsMap.values()).sort((a, b) => b.id - a.id);

    sorted.forEach(n => {
        tbody.appendChild(createTableRow(n));
    });
}

function createTableRow(n) {
    const tr = document.createElement('tr');
    tr.id = `row-${n.id}`;
    tr.addEventListener('click', () => showDetails(n.id));

    tr.innerHTML = `
        <td class="mono font-bold">${n.id}</td>
        <td>${getChannelBadge(n.channel)}</td>
        <td><span class="recipient-truncate" title="${n.recipient}">${n.recipient}</span></td>
        <td class="text-center font-semibold">${n.attempts}</td>
        <td>${getStatusPill(n.status)}</td>
        <td class="mono font-light">${n.nextRetryAt ? formatTime(n.nextRetryAt) : '--'}</td>
    `;
    return tr;
}

function upsertTableRow(n) {
    const tbody = document.getElementById('historyTableBody');
    const existingRow = document.getElementById(`row-${n.id}`);
    const noDataRow = document.getElementById('noDataRow');

    if (noDataRow) {
        noDataRow.remove();
    }

    const newRow = createTableRow(n);

    if (existingRow) {
        existingRow.replaceWith(newRow);
        // Add rapid pulse highlights on status changes
        newRow.classList.add('pulse-update');
        setTimeout(() => newRow.classList.remove('pulse-update'), 1000);
    } else {
        // Prepend new row
        tbody.insertBefore(newRow, tbody.firstChild);
    }
}

// Update counters
function updateMetrics() {
    const list = Array.from(notificationsMap.values());
    const total = list.length;
    
    const successes = list.filter(n => n.status === 'SUCCESS').length;
    const retries = list.filter(n => n.status === 'PENDING' && n.attempts > 0).length;
    const failures = list.filter(n => n.status === 'FAILED').length;
    
    document.getElementById('statSuccess').textContent = successes;
    document.getElementById('statRetry').textContent = retries;
    document.getElementById('statFailure').textContent = failures;

    const rate = total > 0 ? Math.round((successes / total) * 100) : 0;
    document.getElementById('statSuccessRate').textContent = `${rate}%`;
}

// Show Detail Modal
function showDetails(id) {
    const n = notificationsMap.get(id);
    if (!n) return;

    const modalBody = document.getElementById('modalDetailsBody');
    
    let errorSection = '';
    if (n.lastError) {
        errorSection = `
            <div class="details-label">Last Error:</div>
            <div class="details-val error-box mono">${n.lastError}</div>
        `;
    }

    modalBody.innerHTML = `
        <div class="details-grid">
            <div class="details-label">Database ID:</div>
            <div class="details-val font-bold">#${n.id}</div>
            
            <div class="details-label">Channel Type:</div>
            <div class="details-val">${getChannelBadge(n.channel)}</div>
            
            <div class="details-label">Recipient Target:</div>
            <div class="details-val font-semibold">${n.recipient}</div>
            
            <div class="details-label">Subject Header:</div>
            <div class="details-val">${n.title}</div>
            
            <div class="details-label">Message Text:</div>
            <div class="details-val" style="white-space: pre-wrap;">${n.content}</div>
            
            <div class="details-label">Current Status:</div>
            <div class="details-val">${getStatusPill(n.status)}</div>
            
            <div class="details-label">Idempotency Key:</div>
            <div class="details-val mono">${n.idempotencyKey}</div>
            
            <div class="details-label">Total Attempts:</div>
            <div class="details-val font-semibold">${n.attempts}</div>
            
            <div class="details-label">Next Try At:</div>
            <div class="details-val mono">${n.nextRetryAt ? formatDateTimeString(n.nextRetryAt) : '--'}</div>
            
            <div class="details-label">Created Time:</div>
            <div class="details-val font-light">${formatDateTimeString(n.createdAt)}</div>
            
            <div class="details-label">Updated Time:</div>
            <div class="details-val font-light">${formatDateTimeString(n.updatedAt)}</div>

            ${errorSection}
        </div>
    `;

    document.getElementById('detailsModal').classList.remove('hidden');
}

function closeModal() {
    document.getElementById('detailsModal').classList.add('hidden');
}

// Log Terminal Helpers
function appendLog(text, className) {
    const logs = document.getElementById('eventLogs');
    const line = document.createElement('div');
    line.className = `log-line ${className}`;
    line.textContent = `[${new Date().toLocaleTimeString()}] ${text}`;
    logs.appendChild(line);
    
    // Auto-scroll to bottom
    logs.scrollTop = logs.scrollHeight;
}

// Visual badge mappings
function getChannelIcon(channel) {
    if (channel === 'EMAIL') return '<i class="fa-regular fa-envelope" style="color:var(--color-email)"></i>';
    if (channel === 'SMS') return '<i class="fa-solid fa-comment-sms" style="color:var(--color-sms)"></i>';
    if (channel === 'IN_APP') return '<i class="fa-solid fa-mobile-screen-button" style="color:var(--color-in-app)"></i>';
    return '';
}

function getChannelBadge(channel) {
    const icon = getChannelIcon(channel);
    let colorClass = 'email';
    if (channel === 'SMS') colorClass = 'sms';
    if (channel === 'IN_APP') colorClass = 'in-app';
    
    return `<span class="badge info" style="display:inline-flex; gap:0.3rem; align-items:center;">
        ${icon} ${channel}
    </span>`;
}

function getStatusPill(status) {
    const statusLower = status.toLowerCase();
    let pulseClass = '';
    let icon = '';

    if (status === 'SUCCESS') icon = '<i class="fa-solid fa-circle-check"></i> ';
    if (status === 'FAILED') icon = '<i class="fa-solid fa-circle-xmark"></i> ';
    if (status === 'PENDING') icon = '<i class="fa-solid fa-clock"></i> ';
    if (status === 'QUEUED') icon = '<i class="fa-solid fa-list-ol"></i> ';
    if (status === 'PROCESSING') {
        icon = '<i class="fa-solid fa-spinner fa-spin"></i> ';
    }

    return `<span class="status-pill ${statusLower}">${icon}${status}</span>`;
}

// Time formattings
function formatTime(localDateTimeString) {
    if (!localDateTimeString) return '';
    try {
        const date = new Date(localDateTimeString);
        return date.toLocaleTimeString();
    } catch (e) {
        return localDateTimeString;
    }
}

function formatDateTimeString(localDateTimeString) {
    if (!localDateTimeString) return '';
    try {
        const date = new Date(localDateTimeString);
        return date.toLocaleString();
    } catch (e) {
        return localDateTimeString;
    }
}
