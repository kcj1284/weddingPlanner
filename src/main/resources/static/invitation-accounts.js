const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const newGiftAccounts=()=>({enabled:false,title:'마음 전하실 곳',message:'축하해 주시는 따뜻한 마음에 감사드립니다.',accounts:[]});
const sides=[['GROOM','신랑측'],['BRIDE','신부측']];
function row(account,index){
  const field=(key,label,max,extra='')=>`<label>${label}<input data-gift-index="${index}" data-gift-field="${key}" value="${escape(account[key])}" maxlength="${max}" required ${extra}></label>`;
  return `<div class="gift-editor-row"><div class="gift-row-heading"><strong>계좌 ${index+1}</strong><button type="button" class="secondary" data-act="remove-account" data-index="${index}" aria-label="${escape(account.relation||'계좌')} 계좌 삭제">삭제</button></div><div class="field-row">${field('relation','관계 · 신랑, 신부, 아버지 등',30)}${field('holder','예금주',30)}</div>${field('bank','은행명',40)}${field('number','계좌번호',40,'inputmode="numeric" pattern="[0-9]+([ -][0-9]+)*" minlength="5" autocomplete="off"')}<p class="hint">숫자와 하이픈(-), 공백으로 입력하세요.</p></div>`;
}
export function accountControls(data){
  const gift=data.giftAccounts||newGiftAccounts();
  return `<fieldset id="gift-controls"><legend>마음 전하실 곳</legend><label class="gift-toggle"><input type="checkbox" data-gift-setting="enabled" ${gift.enabled?'checked':''}>청첩장에 축의금 계좌 공개하기</label><p class="hint">공개하면 청첩장 링크를 가진 누구나 은행명, 예금주, 계좌번호를 볼 수 있어요. 계좌를 확인한 후 발행해 주세요.</p><label>섹션 제목<input data-gift-setting="title" value="${escape(gift.title)}" maxlength="50" required></label><label>안내 문구<textarea data-gift-setting="message" maxlength="500" rows="3">${escape(gift.message)}</textarea></label>${sides.map(([side,label])=>`<section class="gift-editor-group"><h3>${label} 계좌</h3>${gift.accounts.map((a,i)=>a.side===side?row(a,i):'').join('')}<button type="button" class="secondary" data-act="add-account" data-side="${side}" ${gift.accounts.filter(a=>a.side===side).length>=4?'disabled':''}>${label} 계좌 추가 ＋</button><p class="hint">본인과 혼주 계좌를 최대 4개까지 추가할 수 있어요.</p></section>`).join('')}</fieldset>`;
}
export function collectAccounts(data){
  if(!document.querySelector('#gift-controls'))return;
  const gift=data.giftAccounts??=newGiftAccounts();
  document.querySelectorAll('[data-gift-setting]').forEach(input=>gift[input.dataset.giftSetting]=input.type==='checkbox'?input.checked:input.value.trim());
  document.querySelectorAll('[data-gift-field]').forEach(input=>gift.accounts[Number(input.dataset.giftIndex)][input.dataset.giftField]=input.value.trim());
}
export function accountMarkup(data){
  const gift=data.giftAccounts;if(!gift?.enabled||!gift.accounts.length)return '';
  return `<section class="card-section gift-section"><span class="section-caption">WITH GRATITUDE</span><h2>${escape(gift.title)}</h2><p class="poem">${escape(gift.message)}</p>${sides.map(([side,label])=>{const entries=gift.accounts.map((account,index)=>({account,index})).filter(({account})=>account.side===side);return entries.length?`<details class="gift-group"><summary>${label} 계좌번호 <span aria-hidden="true">＋</span></summary>${entries.map(({account:a,index})=>`<div class="gift-account"><div class="gift-account-heading"><strong>${escape(a.relation)}</strong><span>${escape(a.holder)}</span></div><p>${escape(a.bank)}</p><div class="gift-number-row"><input aria-label="${escape(label+' '+a.relation+' 계좌번호')}" readonly value="${escape(a.number)}"><button type="button" class="card-button" data-act="copy-account" data-index="${index}" aria-label="${escape(label+' '+a.relation+' 계좌번호 복사')}">복사</button></div></div>`).join('')}</details>`:'';}).join('')}</section>`;
}
export async function copyAccount(data,button,notice){
  const account=data.giftAccounts?.accounts[Number(button.dataset.index)];if(!account)return;
  try{await navigator.clipboard.writeText(account.number.replace(/[ -]/g,''));notice(`${account.relation} 계좌번호를 복사했어요.`);}catch{
    const input=button.closest('.gift-number-row').querySelector('input');input.focus();input.select();notice('자동 복사가 어려워요. 선택된 계좌번호를 직접 복사해 주세요.');
  }
}
