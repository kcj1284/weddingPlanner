export const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const safeHttpUrl = value => { try { const u = new URL(value); return ['https:', 'http:'].includes(u.protocol) ? escapeHtml(u.href) : ''; } catch { return ''; } };
export const RESERVATION_LABELS = { UNKNOWN:'예약 미확인', AVAILABLE:'예약 가능 확인', BOOKED:'예약 완료' };
export const dateLabel = value => { const date = new Date(value + '+09:00'); return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('ko-KR', {timeZone:'Asia/Seoul',month:'long',day:'numeric',weekday:'short',hour:'2-digit',minute:'2-digit',hour12:false}).format(date); };

