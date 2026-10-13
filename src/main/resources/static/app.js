import {state,draftVotes,placeSearches,groupShares} from '/js/meeting/meeting-state.js';
import {createMeetingView} from '/js/meeting/meeting-view.js';
import {createAuthView} from '/js/account/auth-view.js';
import {api,csrf} from '/js/shared/api-client.js';
import {escapeHtml,safeHttpUrl,RESERVATION_LABELS,dateLabel} from '/js/shared/view-helpers.js';
import {usernameError,passwordError} from '/credential-policy.js';
const $ = (s, root = document) => root.querySelector(s);
const app = $('#app'), modal = $('#modal'), content = $('#modal-content');
let placeSearchVersion=0,reviewPollTimer;
let kakaoShareReady=false;
let resendInvitation=null;
const {hero,cards,detail,poll,places,manage}=createMeetingView({state,draftVotes,regionSearchControls});
const {authDialog}=createAuthView({state,dialog,$});
function personalInviteDialog(invitation){
  resendInvitation={title:state.meeting.title,label:invitation.label,code:invitation.code,link:location.origin+'/?guest='+encodeURIComponent(invitation.token)};
  dialog(`<h2>초대코드 확인 · 재전송</h2><p>${escapeHtml(invitation.label)}님의 개인 초대입니다.</p><label>초대코드<input id="resend-code" value="${escapeHtml(invitation.code)}" readonly></label><label>개인 초대 링크<input id="resend-link" value="${escapeHtml(resendInvitation.link)}" readonly></label><div class="group-share-actions"><button type="button" class="button secondary" data-action="copy" data-target="resend-code">코드 복사</button><button type="button" class="button secondary" data-action="copy" data-target="resend-link">링크 복사</button><button type="button" class="button kakao-share" data-action="invite-resend">카카오톡으로 재전송</button></div><p class="fine-print">개인 초대는 해당 친구에게만 보내 주세요. 기존 이름과 투표로 이어서 참여할 수 있습니다.</p><button type="button" class="text-button" data-action="share">초대 목록으로 돌아가기</button>`);
}
async function prepareKakaoShare(){
  const config=await api('/kakao-share-config');if(!config.javascriptKey)return;
  if(!window.Kakao)await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='https://t1.kakaocdn.net/kakao_js_sdk/2.8.3/kakao.min.js';script.onload=resolve;script.onerror=reject;document.head.append(script);});
  if(!window.Kakao.isInitialized())window.Kakao.init(config.javascriptKey);kakaoShareReady=true;
}
function placeResults(data,query,type){
  const reviewMessage=data.searchRefreshing?'저장된 결과를 먼저 보여드려요. 최신 장소 목록을 확인 중입니다.':data.pending?'청모 후기 확인 중 · 식당 목록을 먼저 둘러보세요.':data.reviewStatus==='unavailable'?'청모 후기 검색은 네이버 연결 후 제공됩니다.':'청모 관련 글이 확인된 곳을 먼저 표시합니다. 검색된 글은 실제 방문 여부를 보장하지 않습니다.';
  return `<div class="hint">${escapeHtml(data.message)}</div><p class="review-search-status" role="status">${reviewMessage}</p>${data.places.length?`<div class="results">${data.places.map(p=>`<article class="panel result"><span class="badge neutral">${type==='FD6'?'식당':'카페'} · 예약 미확인</span><h3>${escapeHtml(p.name)}</h3><p>${escapeHtml(p.address)}<br>${escapeHtml(p.phone||'전화번호 정보 없음')}</p><div class="result-actions">${safeHttpUrl(p.url)?`<a class="text-button" href="${safeHttpUrl(p.url)}" target="_blank" rel="noopener noreferrer">지도에서 확인 ↗</a>`:''}<button class="button soft" type="button" data-action="result-add" data-place="${escapeHtml(JSON.stringify(p))}" ${state.meeting.status!=='OPEN'?'disabled':''}>후보 추가 ＋</button></div>${p.blogs?.length?`<div class="place-blog-links"><strong>청모 관련 글</strong>${p.blogs.map(blog=>`<a href="${safeHttpUrl(blog.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(blog.title)} ↗<small>${/^\d{8}$/.test(blog.date)?escapeHtml(blog.date.slice(0,4)+'.'+blog.date.slice(4,6)+'.'+blog.date.slice(6)):''}</small></a>`).join('')}</div>`:p.reviewStatus==='pending'?'<p class="small">청모 관련 글 확인 중…</p>':p.reviewStatus==='failed'?'<p class="small">후기 연결이 지연되고 있어요. 잠시 후 다시 검색해 주세요.</p>':p.reviewStatus==='ready'?'<p class="small">현재 확인된 청모 관련 글이 없어요.</p>':''}</article>`).join('')}</div>`:`<div class="empty">${data.configured?'검색 결과가 없거나 연결이 원활하지 않아요. 다른 검색어로 다시 찾아보세요.':'지도에서 장소를 확인한 뒤 직접 추가할 수 있어요.'}<br><a class="button secondary" href="https://map.kakao.com/?q=${encodeURIComponent(query)}" target="_blank" rel="noopener noreferrer">카카오맵에서 찾아보기 ↗</a><br><button type="button" class="add-link" data-action="add-place" ${state.meeting.status!=='OPEN'?'disabled':''}>장소 직접 추가</button></div>`}`;
}
function pollPlaceReviews(id,query,type,version,attempt=0,previous=''){
  if(attempt>=60)return;
  reviewPollTimer=setTimeout(async()=>{
    if(version!==placeSearchVersion||state.meeting?.id!==id||state.tab!=='places'||!$('#search-results'))return;
    try{
      const data=await api('/meetings/'+id+'/places-reviews?'+new URLSearchParams({q:query,type}));
      if(version!==placeSearchVersion||state.meeting?.id!==id||state.tab!=='places')return;
      const signature=JSON.stringify(data);if(signature!==previous)$('#search-results').innerHTML=placeResults(data,query,type);
      if(data.pending){if(attempt===59){$('.review-search-status').textContent='후기는 계속 확인 중입니다. 잠시 후 다시 검색해 주세요.';}else pollPlaceReviews(id,query,type,version,attempt+1,signature);}
    }catch{const status=$('.review-search-status');if(status)status.textContent='후기 연결이 지연되고 있어요. 식당 목록은 그대로 이용할 수 있습니다.';}
  },2000);
}
const searchRegions = {
  서울:['강남','성수','공덕','홍대','여의도','잠실','종로','신촌','용산','왕십리'],
  부산:['서면','해운대','광안리','남포동','동래'],
  대구:['동성로','수성못','범어동','칠곡'],
  인천:['구월동','부평','송도','청라'],
  광주:['상무지구','충장로','첨단','수완지구'],
  대전:['둔산동','유성','은행동','관저동'],
  울산:['삼산동','성남동','태화동'],
  세종:['나성동','새롬동','보람동'],
  경기:['수원 인계동','성남 판교','성남 분당','고양 일산','안양 평촌','부천 중동','용인 수지'],
  강원:['춘천 명동','원주 무실동','강릉 교동'],
  충북:['청주 복대동','청주 성안길','충주 연수동'],
  충남:['천안 불당동','천안 두정동','아산 배방'],
  전북:['전주 객리단길','전주 신시가지','군산 수송동'],
  전남:['여수 웅천','순천 조례동','목포 하당'],
  경북:['포항 영일대','경주 황리단길','구미 인동'],
  경남:['창원 상남동','김해 내외동','진주 평거동'],
  제주:['제주시 연동','제주시 노형동','서귀포 중문']
};
const quickMeetingAreas = [['서울','강남'],['서울','성수'],['서울','홍대'],['서울','잠실'],['서울','여의도']];
function initialPlaceSearch(area='') {
  const city=Object.keys(searchRegions).find(city=>area.startsWith(city))||'';
  const district=city?(searchRegions[city].find(district=>area.includes(district))||''):'';
  return {city,district,keyword:district?area.slice(area.indexOf(district)+district.length).trim():city?area.slice(city.length).trim():area,type:'FD6'};
}
function districtOptions(city,selected='') {return `<option value="">${city?'전체 동네':'동네 선택'}</option>`+(searchRegions[city]||[]).map(district=>`<option value="${escapeHtml(district)}" ${district===selected?'selected':''}>${escapeHtml(district)}</option>`).join('')+`<option value="OTHER" ${selected==='OTHER'?'selected':''}>기타 · 직접 입력</option>`;}
function regionSearchControls(area) {
  const selection=placeSearches.get(state.meeting.id)||initialPlaceSearch(area);
  const customField=(selector,name,label,placeholder)=>`<label class="region-custom-input" hidden>${label}<input name="${name}" value="${escapeHtml(selection[name]||'')}" maxlength="20" pattern=".*\\S.*" disabled placeholder="${placeholder}"></label>`;
  return `<div class="quick-region"><span class="quick-region-title" id="quick-region-title">서울 모임 동네 빠른 선택</span><div class="region-chips" role="group" aria-labelledby="quick-region-title">${quickMeetingAreas.map(([city,district])=>`<button type="button" class="region-chip" data-region-chip="${city}|${district}" aria-pressed="${selection.city===city&&selection.district===district}">${district}</button>`).join('')}<button type="button" class="region-chip" data-region-other>기타</button></div></div><label>시·도<select name="city" required><option value="">시·도 선택</option>${Object.keys(searchRegions).map(city=>`<option value="${city}" ${selection.city===city?'selected':''}>${city}</option>`).join('')}<option value="OTHER" ${selection.city==='OTHER'?'selected':''}>기타 · 직접 입력</option></select></label>${customField('city','customCity','시·도 직접 입력','예: 서울특별시')}<label>동네<select name="district" ${selection.city?'':'disabled'}>${districtOptions(selection.city,selection.district)}</select></label>${customField('district','customDistrict','동네 직접 입력','예: 망원동')}<label class="search-keyword">추가 검색어 · 선택<input name="keyword" value="${escapeHtml(selection.keyword)}" maxlength="50" placeholder="예: 이탈리안, 룸"></label><label class="type-label">장소 종류<select name="type"><option value="FD6" ${selection.type==='FD6'?'selected':''}>식당</option><option value="CE7" ${selection.type==='CE7'?'selected':''}>카페</option></select></label>`;
}
function rememberPlaceSearch(form){
  const city=form.elements.city.value,district=form.elements.district.value;
  form.querySelectorAll('[data-region-chip]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.regionChip===city+'|'+district)));
  placeSearches.set(state.meeting.id,{city,district,customCity:form.elements.customCity.value,customDistrict:form.elements.customDistrict.value,keyword:form.elements.keyword.value,type:form.elements.type.value});
}
function syncCustomRegions(form){
  for(const [selector,inputName] of [['city','customCity'],['district','customDistrict']]){
    const input=form.elements[inputName],active=form.elements[selector].value==='OTHER';
    input.closest('label').hidden=!active;input.disabled=!active;input.required=active;
  }
}
document.addEventListener('click',event=>{
  const button=event.target.closest('[data-region-chip]');if(!button)return;
  const form=button.form,[city,district]=button.dataset.regionChip.split('|');
  form.elements.city.value=city;form.elements.district.innerHTML=districtOptions(city,district);form.elements.district.disabled=false;
  syncCustomRegions(form);
  rememberPlaceSearch(form);
});
document.addEventListener('click',event=>{
  const button=event.target.closest('[data-region-other]');if(!button)return;
  const form=button.form,city=form.elements.city.value||'서울';form.elements.city.value=city;
  form.elements.district.innerHTML=districtOptions(city,'OTHER');form.elements.district.disabled=false;
  syncCustomRegions(form);rememberPlaceSearch(form);form.elements.customDistrict.focus();
});
let toastTimer;
function toast(message) { $('#toast').textContent=message; $('#toast').classList.add('visible'); clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('#toast').classList.remove('visible'),3500); }
function dialog(html) { content.innerHTML=html; const title=$('h2',content); if(title){title.id='dialog-title';modal.setAttribute('aria-labelledby',title.id);} if(!modal.open)modal.showModal(); }
function accountView() {document.querySelectorAll('nav [data-action]').forEach(button=>{button.classList.toggle('active',button.dataset.action==='home');if(button.dataset.action==='home')button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');}); document.querySelectorAll('nav [data-action="new"],nav [data-action="invitation"]').forEach(b=>b.hidden=Boolean(state.user?.guest)); $('#account').innerHTML=state.user ? `<div class="user-chip"><span class="avatar">${escapeHtml(state.user.displayName.slice(0,1))}</span><span>${escapeHtml(state.user.displayName)}님</span>${state.user.guest?'':'<a class="text-button" href="/account.html?mode=settings">복구 이메일</a>'}<button class="text-button" data-action="logout">로그아웃</button></div>` : '<button class="text-button" data-action="login">로그인 ↗</button>'; }
function welcome() {
  accountView(); app.innerHTML=`${new URLSearchParams(location.search).has('invite')?'<div class="join-banner"><div><h3>청모에 초대받으셨네요!</h3><p>로그인하거나 가입한 뒤 초대받은 모임에 참여해 주세요.</p></div><button class="button" data-action="login">참여하러 가기 ↗</button></div>':''}${hero()}<div class="section-top"><h2>만남까지, 세 걸음이면 충분해요.</h2><span class="eyebrow">HOW IT WORKS</span></div><div class="welcome-steps"><article class="step"><span class="step-number">01</span><h3>소중한 사람들 초대</h3><p>모임을 만들고<br>링크 하나로 초대해요.</p></article><article class="step"><span class="step-number">02</span><h3>함께 정하는 약속</h3><p>가능한 시간과 장소에<br>마음을 모아 투표해요.</p></article><article class="step"><span class="step-number">03</span><h3>기분 좋은 만남</h3><p>호스트가 장소를 확인하고<br>우리의 약속을 완성해요.</p></article></div>${state.demo?'<div class="demo-panel"><div><h3>먼저 둘러보고 싶다면</h3><p>예시 모임으로 호스트와 게스트의 화면을 체험해 보세요.</p></div><div class="demo-actions"><button class="button secondary" data-action="demo-host">호스트로 체험</button><button class="button secondary" data-action="demo-guest">게스트로 체험</button></div></div>':''}`;
}
async function loadMeetings(selectId) {
  state.meetings=await api('/meetings');
  const selected=selectId||state.meeting?.id||new URLSearchParams(location.search).get('meeting')||state.meetings[0]?.id;
  state.meeting=selected && state.meetings.some(m=>m.id===selected) ? await api('/meetings/'+selected) : null;
  if(state.meeting?.status==='CONFIRMED')draftVotes.delete(state.meeting.id);
  if(state.meeting?.role!=='HOST')state.tab='vote'; render();
}
function render() {
  accountView();
  app.innerHTML=`<div class="intro-head"><div><span class="eyebrow">MY GATHERINGS</span><h1>${escapeHtml(state.user.displayName)}님의 청모</h1><p class="muted">${state.user.guest?'초대받은 모임에서 가능한 일정과 장소를 선택해 주세요.':'설레는 소식을 전할 만남을 준비해요.'}</p></div>${state.user.guest?'':'<button class="button secondary" data-action="new">＋ 모임 만들기</button>'}</div><div class="section-top"><h2>함께하는 모임 <span class="small">${state.meetings.length}개의 소중한 약속</span></h2><button class="text-button" data-action="refresh">↻ 새로고침</button></div>${state.meetings.length?`<div class="meeting-list">${cards()}</div>`:'<div class="empty">아직 준비 중인 청모가 없어요.<br>새 모임을 만들거나 초대 링크로 참여해 보세요.<br><button class="button soft" data-action="join">초대받은 모임 참여</button></div>'}${state.meeting?detail():''}`; updateVoteSummary();
  const searchForm=$('#search-form');if(searchForm)syncCustomRegions(searchForm);
  if(new URLSearchParams(location.search).has('invite'))joinDialog(new URLSearchParams(location.search).get('invite'));
}
function newDialog() {
  dialog('<h2>새로운 청모를 열어요.</h2><p class="muted">소중한 사람들과 함께할 만남을 준비해요.</p><form id="new-form"><label>모임 이름<input name="title" required maxlength="80" placeholder="예: 지은과 도윤의 청첩장 모임"></label><label>초대의 한마디<textarea name="description" maxlength="500" placeholder="좋은 소식을 전하고 싶어요. 함께 만나요!"></textarea></label><div class="row"><label>만나고 싶은 지역<input name="area" required maxlength="80" placeholder="서울 성수"></label><label>예상 인원<input name="expectedCount" type="number" min="2" max="100" value="6" required></label></div><p class="form-error" role="alert"></p><button class="button" type="submit">모임 만들고 초대하기 ↗</button></form>');
}
function joinDialog(token='') {dialog(`<h2>초대코드로 참여해요.</h2><p class="muted">호스트에게 받은 개인 초대코드를 입력해 주세요. 가입 없이 참여할 수 있어요.</p><form id="join-form"><label>초대코드<input name="token" value="${escapeHtml(token)}" required maxlength="500" autocomplete="off" spellcheck="false" placeholder="예: A7K3M9" autocapitalize="characters"></label><p class="fine-print">대문자 영문·숫자 6자리예요. 전용 초대 링크를 붙여 넣어도 돼요.</p><p class="form-error" role="alert"></p><button class="button" type="submit">초대코드 확인하기 ↗</button></form>`);}
function guestJoinDialog(token,preview) {dialog(`<h2>${escapeHtml(preview.title)}</h2><p class="muted">초대코드를 확인했어요. 가입 없이 참여해요.</p><form id="guest-form" data-token="${escapeHtml(token)}">${preview.joined?'<p>기존 이름과 투표로 이어서 참여할 수 있어요.</p>':'<label>모임에서 사용할 이름<input name="displayName" autocomplete="nickname" required maxlength="30" placeholder="친구들이 알아볼 수 있는 이름"></label>'}<p class="fine-print">코드는 한 사람의 참여 권한을 담고 있으니 다른 사람에게 공유하지 마세요.${state.user&&!state.user.guest?' 참여하면 호스트 계정에서 게스트 화면으로 전환됩니다.':''}</p><p class="form-error" role="alert"></p><button class="button" type="submit">${preview.joined?'이어서 참여하기':'이름으로 참여하기'} ↗</button></form><button class="auth-switch" data-action="join">다른 초대코드 입력하기</button>`);}

function addDialog(kind, prefill={}) {
  dialog(`<h2>${kind==='TIME'?'언제 만나면 좋을까요?':'좋은 장소를 제안해요.'}</h2><p class="muted">${kind==='TIME'?'날짜와 시간을 함께 선택해 주세요. 한국 시간 기준이에요.':'예약 가능 여부는 먼저 미확인으로 표시해요.'}</p><form id="option-form" data-kind="${kind}"><label>${kind==='TIME'?'날짜·시간':'장소 이름'}<input name="title" ${kind==='TIME'?'type="datetime-local"':'maxlength="120"'} value="${escapeHtml(prefill.name||'')}" required></label><label>${kind==='TIME'?'한마디 (선택)':'주소·장소 설명 (선택)'}<input name="detail" maxlength="300" value="${escapeHtml(prefill.address||'')}" placeholder="${kind==='TIME'?'예: 퇴근 후 저녁':'예: 성수역 3번 출구 근처'}"></label>${kind==='PLACE'?`<label>지도 상세 링크 (선택)<input name="mapUrl" type="url" maxlength="500" value="${escapeHtml(prefill.url||'')}" placeholder="https://…"></label><label>문의 전화번호 (선택)<input name="phone" maxlength="40" value="${escapeHtml(prefill.phone||'')}" placeholder="02-000-0000"></label>`:''}<p class="form-error" role="alert"></p><button class="button" type="submit">투표 후보에 추가하기 ＋</button></form>`);
}
async function login(username,password) {await api('/login',{method:'POST',body:new URLSearchParams({username,password})});await csrf();state.user=await api('/me');draftVotes.clear();state.meeting=null;state.tab='vote';await loadMeetings();}
async function refreshed(message) {await loadMeetings(state.meeting?.id);if(message)toast(message);}
document.addEventListener('click',async event=>{
  const button=event.target.closest('[data-action]');if(!button)return;
  const action=button.dataset.action;
  try {
    if(action==='invitation'){location.href='/invitation.html';return;}
    if(action==='login'||action==='signup'){authDialog(action==='signup');return;}
    if(action==='demo-host'||action==='demo-guest'){button.disabled=true;await login(action==='demo-host'?'demo_host':'demo_guest','cheongmo-demo');modal.close();toast('예시 모임을 체험하고 있어요.');return;}
    if(action==='logout'){await api('/logout',{method:'POST'});await csrf();state.user=null;state.meeting=null;draftVotes.clear();welcome();return;}
    if(action==='join'){joinDialog();return;}
    if(!state.user){authDialog(action==='new');return;}
    if(action==='home'){state.tab='vote';await refreshed();window.scrollTo({top:0,behavior:'smooth'});}
    if(action==='new'){if(state.user.guest)authDialog(true);else newDialog();}
    if(action==='refresh')await refreshed('최신 투표 현황을 불러왔어요.');
    if(action==='select'){state.meeting=await api('/meetings/'+button.dataset.id);state.tab='vote';render();$('#meeting-detail').scrollIntoView({behavior:'smooth',block:'start'});}
    if(action==='tab'){state.tab=button.dataset.tab;render();document.getElementById('tab-'+state.tab).focus({preventScroll:true});$('#meeting-detail').scrollIntoView({behavior:'smooth',block:'start'});}
    if(action==='add-time')addDialog('TIME');
    if(action==='add-place')addDialog('PLACE');
    if(action==='share')await shareDialog();
    if(action==='invite-code'){
      const invitation=await api('/meetings/'+state.meeting.id+'/guest-links/'+button.dataset.id+'/code');
      if(invitation.recoverable)personalInviteDialog(invitation);
      else dialog(`<h2>새 초대코드가 필요해요.</h2><p>${escapeHtml(invitation.label)}님의 ${invitation.expired?'초대가 만료되었습니다.':'기존 코드는 원문이 저장되지 않아 확인할 수 없습니다.'}</p><p class="muted">새 코드를 발급하면 이전 코드와 링크는 사용할 수 없게 됩니다. 참여자의 이름과 투표는 그대로 유지됩니다. 새 코드는 30일 동안 유효합니다.</p><button type="button" class="button" data-action="invite-reissue" data-id="${escapeHtml(button.dataset.id)}">새 코드 발급 후 재전송</button><button type="button" class="text-button" data-action="share">초대 목록으로 돌아가기</button>`);
    }
    if(action==='invite-reissue')personalInviteDialog(await api('/meetings/'+state.meeting.id+'/guest-links/'+button.dataset.id+'/reissue',{method:'POST'}));
    if(action==='invite-resend'){
      if(!resendInvitation)throw new Error('초대코드를 다시 확인해 주세요.');
      const text=resendInvitation.title+'\n'+resendInvitation.label+'님, 청모에 초대합니다.\n초대코드: '+resendInvitation.code;
      if(kakaoShareReady)window.Kakao.Share.sendDefault({objectType:'text',text,link:{webUrl:resendInvitation.link,mobileWebUrl:resendInvitation.link},buttonTitle:'청모 참여하기'});
      else if(navigator.share){try{await navigator.share({title:resendInvitation.title,text,url:resendInvitation.link});}catch(error){if(error.name!=='AbortError')throw new Error('링크 복사로 초대를 전달해 주세요.');}}
      else{try{await navigator.clipboard.writeText(text+'\n'+resendInvitation.link);toast('초대 문구를 복사했어요. 해당 친구에게 붙여 넣어 주세요.');}catch{$('#resend-link').select();toast('선택된 링크를 복사해 전달해 주세요.');}}
    }
    if(action==='group-create'){
      const created=await api('/meetings/'+state.meeting.id+'/group-links',{method:'POST'});
      groupShares.set(state.meeting.id,{id:created.id,link:location.origin+'/?group='+encodeURIComponent(created.token)});await shareDialog();
    }
    if(action==='group-kakao'){
      const invitation=groupShares.get(state.meeting.id);if(!invitation)throw new Error('단체 초대 링크를 먼저 만들어 주세요.');
      const text=state.meeting.title+'\n청첩장 모임에 초대합니다. 이름을 입력하고 함께 날짜와 장소를 골라 주세요.';
      if(kakaoShareReady){window.Kakao.Share.sendDefault({objectType:'text',text,link:{webUrl:invitation.link,mobileWebUrl:invitation.link},buttonTitle:'청모 참여하기'});}
      else if(navigator.share){try{await navigator.share({title:state.meeting.title,text,url:invitation.link});}catch(error){if(error.name!=='AbortError')throw new Error('공유가 열리지 않으면 링크 복사로 카카오톡 단체방에 전달해 주세요.');}}
      else{try{await navigator.clipboard.writeText(text+'\n'+invitation.link);toast('초대 문구를 복사했어요. 카카오톡 단체방에 붙여 넣어 주세요.');}catch{$('#group-link').select();toast('초대 링크를 복사해서 카카오톡 단체방에 붙여 넣어 주세요.');}}
    }
    if(action==='group-revoke'){
      dialog(`<h2>단체 초대를 닫을까요?</h2><p>이 링크로 새로 참여할 수 없게 됩니다. 이미 참여한 친구의 투표와 개인 참여 링크는 유지됩니다.</p><button class="button" data-action="group-revoke-confirm" data-id="${escapeHtml(button.dataset.id)}">단체 초대 닫기</button>`);
    }
    if(action==='group-revoke-confirm'){
      await api('/meetings/'+state.meeting.id+'/group-links/'+button.dataset.id,{method:'DELETE'});
      if(groupShares.get(state.meeting.id)?.id===button.dataset.id)groupShares.delete(state.meeting.id);await shareDialog();
    }
    if(action==='revoke-link'){dialog(`<h2>초대를 취소할까요?</h2><p>이 게스트의 접근 권한과 기존 투표가 삭제됩니다.</p><button class="button" data-action="revoke-confirm" data-id="${escapeHtml(button.dataset.id)}">초대 취소하기</button>`);}
    if(action==='revoke-confirm'){await api('/meetings/'+state.meeting.id+'/guest-links/'+button.dataset.id,{method:'DELETE'});await refreshed();await shareDialog();}
    if(action==='copy'){const input=document.getElementById(button.dataset.target||'invite-link');try{await navigator.clipboard.writeText(input.value);toast('초대 정보를 복사했어요.');}catch{input.select();toast('선택된 초대 정보를 복사해 주세요.');}}
    if(action==='reopen'){dialog('<h2>투표를 다시 열까요?</h2><p class="muted">확정된 날짜와 장소가 해제돼요. 기존 후보와 투표는 유지됩니다.</p><button class="button" data-action="reopen-confirm">투표 다시 열기</button>');}
    if(action==='reopen-confirm'){await api('/meetings/'+state.meeting.id+'/reopen',{method:'POST'});modal.close();await refreshed('투표를 다시 열었어요.');}
    if(action==='reservation'){
      const o=state.meeting.options.find(o=>o.id===button.dataset.id);
      dialog(`<h2>예약 확인을 기록해요.</h2><p class="muted">${escapeHtml(o.title)}</p><form id="reservation-form" data-id="${escapeHtml(o.id)}"><label>확인 상태<select name="status">${Object.entries(RESERVATION_LABELS).map(([k,v])=>`<option value="${k}" ${k===o.reservation?'selected':''}>${v}</option>`).join('')}</select></label><label>확인 메모<textarea name="note" maxlength="300" placeholder="예: 10월 8일 전화 확인 / 6명 예약">${escapeHtml(o.reservationNote)}</textarea></label><p class="form-error" role="alert"></p><button class="button" type="submit">확인 내용 저장 ✓</button></form>`);
    }
    if(action==='result-add')addDialog('PLACE',JSON.parse(button.dataset.place));
  }catch(error){toast(error.message);}finally{button.disabled=false;}
});
document.addEventListener('submit',async event=>{
  const form=event.target;if(!(form instanceof HTMLFormElement))return;event.preventDefault();
  const fields=Object.fromEntries(new FormData(form));const submit=$('button[type="submit"]',form);const failure=$('.form-error',form);
  form.setAttribute('aria-busy','true');if(submit)submit.disabled=true;if(failure)failure.textContent='';
  try{
    if(form.id==='guest-form'){const group=form.dataset.group==='true',token=form.dataset.token||new URLSearchParams(location.search).get(group?'group':'guest');const joined=await api(group?'/group-session':'/guest-session',{method:'POST',body:{token,displayName:fields.displayName||''}});await csrf();state.user=await api('/me');draftVotes.clear();state.meeting=null;const personal=group?joined.guestToken:token;history.replaceState(null,'','/?'+(personal?'guest='+encodeURIComponent(personal)+'&':'')+'meeting='+joined.meetingId);modal.close();await loadMeetings(joined.meetingId);toast('모임에 참여했어요. 가능한 약속을 골라 주세요.');}
    if(form.id==='guest-link-form'){const created=await api('/meetings/'+state.meeting.id+'/guest-links',{method:'POST',body:fields});await shareDialog(location.origin+'/?guest='+encodeURIComponent(created.token),created.code);}
    if(form.id==='auth-form') {const signup=form.dataset.signup==='true';if(signup){const error=usernameError(fields.username)||passwordError(fields.password);if(error)throw new Error(error);if(fields.password!==fields.passwordConfirmation)throw new Error('두 비밀번호가 일치하지 않아요.');}if(signup)await api('/register',{method:'POST',body:fields});await login(fields.username,fields.password);if(!new URLSearchParams(location.search).has('invite'))modal.close();if(signup){sessionStorage.setItem('cheongmo.recoveryEmail',fields.recoveryEmail);try{const sent=await api('/account/verify-request',{method:'POST',body:{email:fields.recoveryEmail,password:fields.password}});sessionStorage.setItem('cheongmo.recoveryNotice',sent.message+(sent.localMailbox?' 개발 환경에서는 artifacts/mailbox의 메일 파일을 확인해 주세요.':''));}catch(error){sessionStorage.setItem('cheongmo.recoveryNotice','가입은 완료했어요. '+error.message+' 아래에서 다시 인증 메일을 요청할 수 있어요.');}location.href='/account.html?mode=settings';}}
    if(form.id==='new-form'){const created=await api('/meetings',{method:'POST',body:{...fields,expectedCount:Number(fields.expectedCount)}});modal.close();await loadMeetings(created.id);toast('청모를 만들었어요. 친구들을 초대해 주세요.');$('#meeting-detail').scrollIntoView({behavior:'smooth'});await shareDialog();}
    if(form.id==='join-form'){let token=fields.token.trim();if(token.includes('?')){try{token=new URL(token).searchParams.get('guest')||'';}catch{throw new Error('초대코드 또는 전용 링크를 확인해 주세요.');}}const preview=await api('/guest-preview/'+encodeURIComponent(token));guestJoinDialog(token,preview);}
    if(form.id==='option-form'){await api('/meetings/'+state.meeting.id+'/options',{method:'POST',body:{kind:form.dataset.kind,title:fields.title,detail:fields.detail,mapUrl:fields.mapUrl||'',phone:fields.phone||''}});modal.close();await refreshed('투표 후보를 추가했어요.');}
    if(form.id==='vote-form'){await api('/meetings/'+state.meeting.id+'/votes',{method:'PUT',body:{optionIds:new FormData(form).getAll('optionIds')}});draftVotes.delete(state.meeting.id);await refreshed('나의 투표를 저장했어요.');$('#meeting-detail').scrollIntoView({block:'start'});}
    if(form.id==='confirm-form'){await api('/meetings/'+state.meeting.id+'/confirm',{method:'POST',body:fields});state.tab='vote';await refreshed('우리의 약속이 정해졌어요!');}
    if(form.id==='reservation-form'){await api('/meetings/'+state.meeting.id+'/options/'+form.dataset.id+'/reservation',{method:'PUT',body:fields});modal.close();await refreshed('예약 확인 내용을 기록했어요.');}
    if(form.id==='search-form'){
      fields.q=[fields.city==='OTHER'?fields.customCity:fields.city,fields.district==='OTHER'?fields.customDistrict:fields.district,fields.keyword].map(value=>(value||'').trim()).filter(Boolean).join(' ');
      const result=$('#search-results');result.innerHTML='<div class="loading">함께할 장소를 찾고 있어요…</div>';
      const version=++placeSearchVersion,id=state.meeting.id;clearTimeout(reviewPollTimer);
      try {const data=await api('/meetings/'+id+'/places?'+new URLSearchParams({q:fields.q,type:fields.type}));
        if(version!==placeSearchVersion||state.meeting?.id!==id||state.tab!=='places')return;
        result.innerHTML=placeResults(data,fields.q,fields.type);
        if(data.pending)pollPlaceReviews(id,fields.q,fields.type,version,0,JSON.stringify(data));
      }catch(error){result.innerHTML=`<div class="empty">${escapeHtml(error.message)}</div>`;}
    }
  }catch(error){if(failure)failure.textContent=error.message;else toast(error.message);}
  finally{form.setAttribute('aria-busy','false');if(submit)submit.disabled=false;}
});
function updateVoteSummary() {
  const summary=$('#vote-summary'), hint=$('#vote-hint'), m=state.meeting;
  if(!summary||!m)return;
  const ids=draftVotes.get(m.id)||m.myVotes;
  const count=kind=>m.options.filter(o=>o.kind===kind&&ids.includes(o.id)).length;
  summary.textContent=`일정 ${count('TIME')}개 · 장소 ${count('PLACE')}개 선택`;
  const dirty=ids.length!==m.myVotes.length||ids.some(id=>!m.myVotes.includes(id));
  if(!dirty)draftVotes.delete(m.id);
  hint.textContent=dirty?'아직 저장하지 않았어요. 투표 저장을 눌러 주세요.':ids.length?'저장된 선택이에요. 언제든 수정할 수 있어요.':'모두 해제하고 저장하면 투표가 철회돼요.';
  summary.closest('.save-bar').classList.toggle('unsaved',dirty);
}
document.addEventListener('change',event=>{
  if(event.target.form?.id==='search-form'){
    const form=event.target.form,city=form.elements.city,district=form.elements.district;
    if(event.target===city){
      district.innerHTML=districtOptions(city.value);district.disabled=!city.value;
    }
    syncCustomRegions(form);
    rememberPlaceSearch(form);
  }
  if(event.target.name==='optionIds'){
    event.target.closest('.option-card').classList.toggle('chosen',event.target.checked);
    draftVotes.set(state.meeting.id,new FormData($('#vote-form')).getAll('optionIds'));
    updateVoteSummary();
  }
});
document.addEventListener('keydown',event=>{
  if(!event.target.matches('[role="tab"]')||!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
  event.preventDefault();
  const tabs=[...document.querySelectorAll('[role="tab"]')], index=tabs.indexOf(event.target);
  const next=event.key==='Home'?0:event.key==='End'?tabs.length-1:(index+(event.key==='ArrowRight'?1:-1)+tabs.length)%tabs.length;
  state.tab=tabs[next].dataset.tab;render();document.getElementById('tab-'+state.tab).focus({preventScroll:true});
});
window.addEventListener('beforeunload',event=>{
  if([...draftVotes].some(([id,ids])=>{const m=state.meeting;return id!==m?.id||ids.length!==m.myVotes.length||ids.some(v=>!m.myVotes.includes(v));})){event.preventDefault();event.returnValue='';}
});
$('#close-modal').onclick=()=>modal.close();
modal.addEventListener('click',event=>{if(event.target===modal){const rect=modal.getBoundingClientRect();if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)modal.close();}});

async function guestEntry() {
  accountView();
  try {
    const group=new URLSearchParams(location.search).has('group');
    const preview=await api((group?'/group-preview/':'/guest-preview/')+encodeURIComponent(new URLSearchParams(location.search).get(group?'group':'guest')));
    app.innerHTML=`<section class="panel guest-entry"><span class="eyebrow">YOU'RE INVITED</span><h1>${escapeHtml(preview.title)}</h1><p>가입 없이 초대받은 청모에 참여해요.</p><form id="guest-form" data-group="${group}">${preview.joined?'<p>다시 오셨네요. 기존 이름과 투표로 이어서 참여해요.</p>':'<label>모임에서 사용할 이름<input name="displayName" autocomplete="nickname" required maxlength="30" placeholder="친구들이 알아볼 수 있는 이름"></label>'}<p class="fine-print">${group?'단체방 초대 링크입니다. 친구마다 이름과 투표가 따로 저장돼요.':'이 링크는 한 사람의 참여 권한을 담고 있어요. 다른 사람에게 공유하지 마세요.'} ${state.user&&!state.user.guest?'참여하면 호스트 계정에서 게스트 화면으로 전환됩니다.':''}</p><p class="form-error" role="alert"></p><button class="button" type="submit">${preview.joined?'이어서 참여하기':'이름으로 참여하기'} ↗</button></form></section>`;
  } catch(error) {app.innerHTML=`<section class="panel guest-entry"><h1>초대 링크를 확인해 주세요.</h1><p>${escapeHtml(error.message)}</p><p>호스트에게 새 링크를 요청하거나 다른 초대코드를 입력해 주세요.</p><div class="recovery-actions"><button class="button" type="button" data-action="join">다른 초대코드 입력하기</button><a class="button secondary" href="/">홈으로 돌아가기</a></div></section>`;}
}
async function shareDialog(link='',code='') {
  const groups=await api('/meetings/'+state.meeting.id+'/group-links');
  await prepareKakaoShare().catch(()=>{kakaoShareReady=false;});
  const groupShare=groupShares.get(state.meeting.id);
  const groupMarkup=`<section class="group-invite-panel"><h3>단체방에 한 번에 초대</h3><p class="muted">공통 링크 하나를 단체방에 보내세요. 친구마다 이름과 투표는 따로 저장돼요.</p>${groupShare?`<label>단체 초대 링크<input id="group-link" value="${escapeHtml(groupShare.link)}" readonly></label><div class="group-share-actions"><button class="button kakao-share" type="button" data-action="group-kakao">카카오톡 단체방 공유</button><button class="button secondary" type="button" data-action="copy" data-target="group-link">링크 복사</button></div>`:'<button class="button" type="button" data-action="group-create">단체 초대 링크 만들기</button>'}<p class="fine-print">${kakaoShareReady?'카카오톡에서 보낼 친구 또는 단체방을 직접 선택해 주세요.':'모바일 공유 메뉴에서 카카오톡을 선택하세요. 공유를 지원하지 않는 환경에서는 초대 문구를 복사합니다.'} ${location.hostname==='localhost'||location.hostname==='127.0.0.1'?'현재 로컬 링크는 다른 사람의 휴대폰에서 열리지 않습니다. 외부 초대는 배포 주소에서 사용하세요.':''} 링크는 30일 동안 유효하며 링크를 가진 사람은 참여할 수 있어요. 생성한 단체 링크는 이 화면에서 복사해 보관해 주세요. 새로고침 후에는 기존 링크를 다시 확인할 수 없습니다.</p>${groups.map((g,i)=>`<div class="guest-link-row"><div><strong>단체 초대 ${groups.length-i}</strong><p class="small">${g.revoked?'초대 닫힘':g.expired?'초대 만료':'초대 가능'} · ${escapeHtml(g.expiresAt.slice(0,10))} 만료</p></div>${g.revoked?'':`<button type="button" class="text-button" data-action="group-revoke" data-id="${escapeHtml(g.id)}">단체 초대 닫기</button>`}</div>`).join('')}</section>`;
  const links=await api('/meetings/'+state.meeting.id+'/guest-links');
  dialog(`<h2>친구들을 초대해요.</h2>${groupMarkup}<h3>한 사람씩 초대</h3><p class="muted">친구마다 초대코드와 전용 링크를 만들어요. 가입 없이 초대코드 또는 전용 링크를 확인하고 이름을 입력해 참여해요.</p><form id="guest-link-form"><label>초대 대상 이름<input name="label" required maxlength="30" placeholder="예: 민지에게"></label><p class="form-error" role="alert"></p><button class="button" type="submit">초대코드·링크 만들기</button></form>${link?`<label for="invite-code">방금 만든 초대코드</label><div class="copy-row"><input id="invite-code" value="${escapeHtml(code)}" readonly><button class="button" data-action="copy" data-target="invite-code">코드 복사</button></div><label for="invite-link">방금 만든 전용 링크</label><div class="copy-row"><input id="invite-link" value="${escapeHtml(link)}" readonly><button class="button" data-action="copy">복사</button></div><p class="fine-print">지금 복사해 전달해 주세요. 초대 목록의 코드 확인으로 다시 확인하고 전달할 수 있습니다. 30일 동안 접속할 수 있어요.</p>`:''}<h3>초대 목록</h3>${links.map(l=>`<div class="guest-link-row"><div><strong>${escapeHtml(l.label)}</strong><p class="small">${l.revoked?'초대 취소됨':l.expired?'초대 만료':l.displayName?escapeHtml(l.displayName)+' · 참여 중':'아직 참여 전'} · ${escapeHtml(l.expiresAt.slice(0,10))} 만료</p></div>${l.revoked?'':`<div class="guest-link-actions"><button type="button" class="button secondary" data-action="invite-code" data-id="${escapeHtml(l.id)}">코드 확인 · 재전송</button><button type="button" class="text-button" data-action="revoke-link" data-id="${escapeHtml(l.id)}">초대 취소</button></div>`}</div>`).join('')||'<p class="muted">아직 만든 초대가 없어요.</p>'}`);
}

try{
  await csrf();state.demo=(await api('/config')).demoEnabled;state.providers=await api('/auth/providers');
  try{state.user=await api('/me');}catch(error){if(error.status!==401)throw error;}
  if(new URLSearchParams(location.search).has('guest')||new URLSearchParams(location.search).has('group'))await guestEntry();else if(state.user)await loadMeetings();else welcome();
  if(new URLSearchParams(location.search).has('authError'))toast('소셜 로그인에 실패했어요. 다시 시도해 주세요.');
}catch(error){app.innerHTML=`<div class="empty">${escapeHtml(error.message)}<br><button class="button secondary" data-action="reload">다시 불러오기</button></div>`;document.querySelector('[data-action="reload"]').onclick=()=>location.reload();}
finally{app.setAttribute('aria-busy','false');}

document.addEventListener('input',event=>{if(event.target.form?.id==='join-form'&&event.target.name==='token'&&!event.target.value.includes('?')&&!event.target.value.includes('://'))event.target.value=event.target.value.replace(/\s/g,'').toUpperCase();});
