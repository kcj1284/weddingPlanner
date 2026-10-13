const steps=['디자인','예식 정보','사진','문구 편집','축의금','확인·공유'];
const tips=['마음에 드는 디자인을 고르세요. 나중에도 바꿀 수 있어요.','두 사람의 이름과 예식 날짜·장소를 입력하세요.','대표 사진과 갤러리를 추가하세요. 사진 없이도 만들 수 있어요.','원하는 경우 글자와 QR을 꾸미세요. 기본 디자인을 그대로 사용해도 좋아요.','계좌 공개는 선택사항이에요. 공개할 정보만 입력하세요.','입력 내용과 공개 범위를 확인하고 저장·발행하세요.'];
let activeStep=0,view='edit',options={},status={dirty:false,card:null,data:{}},editScroll=0;
const icon=name=>`<img src="/icons/${name}.svg" width="18" height="18" alt="">`;
const requiredFields=[['groom','신랑 이름'],['bride','신부 이름'],['greeting','초대의 글'],['dateTime','예식 날짜와 시간'],['venue','예식장'],['address','예식장 주소']];
function updateReview(){
  const list=document.querySelector('#studio-review-list');if(!list)return;
  const data=status.data||{};list.replaceChildren();
  for(const [key,label] of requiredFields){const row=document.createElement('div'),term=document.createElement('dt'),value=document.createElement('dd');term.textContent=label;value.textContent=data[key]?key==='dateTime'?data[key].replace('T',' '):data[key]:'아직 입력하지 않았어요';row.classList.toggle('missing',!data[key]);row.append(term,value);list.append(row);}
  const complete=requiredFields.filter(([key])=>String(data[key]||'').trim()).length;
  document.querySelector('#studio-review-complete').textContent=`필수 정보 ${complete} / ${requiredFields.length} 입력됨`;
  document.querySelector('#studio-review-privacy').textContent=data.giftAccounts?.enabled?'축의금 계좌를 공개합니다. 은행·예금주·계좌번호를 다시 확인하세요.':'축의금 계좌는 공개하지 않습니다.';
  document.querySelector('#studio-review-publication').textContent=status.card?.published?'현재 발행 중입니다. 저장하면 공개 청첩장에도 바로 반영됩니다.':'현재 비공개입니다. 발행해야 링크를 받은 사람이 볼 수 있어요.';
}
export function syncStudioStatus(next){
  status=next;const node=document.querySelector('#studio-toolbar-status');
  if(node){node.textContent=next.dirty?'저장하지 않은 변경이 있어요':next.card?next.card.published?'발행 중 · 저장하면 공개 반영':'저장됨 · 비공개':'새 청첩장 · 비공개';node.classList.toggle('unsaved',next.dirty);}
  const paper=next.data?.template==='NEWSPAPER',free=document.querySelector('#studio-step-3 fieldset');if(free)free.hidden=paper;
  const newspaper=document.querySelector('#newspaper-controls');if(newspaper)newspaper.hidden=!paper;
  const description=document.querySelector('#studio-step-description');if(description)description.textContent=activeStep===3&&paper?'신문의 소개·추억·인터뷰·감사 문구를 편집하세요.':tips[activeStep];
  updateReview();
}
function showStep(index,focus=false){
  activeStep=Math.max(0,Math.min(steps.length-1,index));
  document.querySelectorAll('.studio-step-panel').forEach((panel,i)=>panel.hidden=i!==activeStep);
  document.querySelectorAll('[data-studio-step]').forEach((button,i)=>{button.setAttribute('aria-selected',String(i===activeStep));button.tabIndex=i===activeStep?0:-1;});
  const title=document.querySelector('#studio-step-title');if(title)title.textContent=steps[activeStep];
  const count=document.querySelector('#studio-step-count');if(count)count.textContent=`제작 ${activeStep+1} / ${steps.length}`;
  const progress=document.querySelector('#studio-step-progress');if(progress)progress.value=activeStep+1;
  const jump=document.querySelector('#studio-step-jump');if(jump)jump.value=String(activeStep);
  const previous=document.querySelector('[data-studio-wizard=previous]');if(previous)previous.disabled=activeStep===0;
  const next=document.querySelector('[data-studio-wizard=next]');if(next)next.textContent=activeStep===steps.length-1?'전체 미리보기':'다음 · '+steps[activeStep+1];
  syncStudioStatus(status);if(focus)title?.focus({preventScroll:true});
}
function showView(next){
  view=next;document.body.dataset.studioView=next;
  document.querySelectorAll('button[data-studio-view]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.studioView===next)));
  const toggle=document.querySelector('#studio-view-toggle');if(toggle)toggle.innerHTML=icon(next==='preview'?'pencil-line':'eye')+(next==='preview'?'편집으로':'미리보기');
}
function alignView(){if(!matchMedia('(max-width:760px)').matches)return;const target=view==='preview'?document.querySelector('.preview-heading'):document.querySelector('.studio-guide');target?.scrollIntoView({block:'start',behavior:'auto'});}
function changeView(next){
  const mobile=matchMedia('(max-width:760px)').matches;if(mobile&&view==='edit'&&next==='preview')editScroll=window.scrollY;
  showView(next);if(mobile&&next==='edit')window.scrollTo({top:editScroll,behavior:'auto'});else alignView();
}
export function revealInvalid(input){const panel=input.closest('.studio-step-panel');if(panel)showStep(Number(panel.dataset.step));showView('edit');input.focus();}
function canAdvance(){
  const panel=document.querySelector('#studio-step-'+activeStep);if(!panel)return true;
  for(const input of panel.querySelectorAll('input,select,textarea')){
    if(input.disabled||input.closest('[hidden]')||input.type==='file')continue;
    if(input.required&&!input.value.trim())input.setCustomValidity('이 항목을 입력해 주세요.');else input.setCustomValidity('');
    if(!input.checkValidity()){input.focus();input.reportValidity();return false;}
  }return true;
}
export function enhanceEditor(config){
  options=config;activeStep=0;view='edit';editScroll=0;document.body.classList.add('studio-edit-page');
  const intro=document.querySelector('.studio-intro');intro.querySelector('h1').textContent='우리의 모바일 청첩장';intro.querySelector('p').textContent='한 단계씩 채우고, 완성된 초대장을 전하세요.';
  const form=document.querySelector('#editor-form'),fields=[...form.querySelectorAll(':scope > fieldset')],sharing=form.querySelector('.save-controls');form.noValidate=true;
  const review=document.createElement('section');review.className='studio-review';review.innerHTML='<h3>발행 전 마지막 확인</h3><p id="studio-review-complete"></p><dl id="studio-review-list"></dl><p id="studio-review-privacy"></p><p id="studio-review-publication" class="hint"></p>';sharing.prepend(review);
  const groups=[[fields[0]],[fields[1],fields[2]],[fields[3]],[fields[4]],[fields[5]],[sharing]];
  groups.forEach((nodes,i)=>{const panel=document.createElement('section');panel.className='studio-step-panel';panel.id='studio-step-'+i;panel.dataset.step=i;panel.setAttribute('role','tabpanel');panel.setAttribute('aria-labelledby','studio-tab-'+i);nodes.forEach(node=>panel.append(node));form.append(panel);});
  const newspaper=document.querySelector('#newspaper-controls');if(newspaper)document.querySelector('#studio-step-3').append(newspaper);
  const guide=document.createElement('section');guide.className='studio-guide';guide.innerHTML='<div class="studio-guide-top"><span id="studio-step-count"></span><label>단계 바로가기<select id="studio-step-jump">'+steps.map((name,i)=>`<option value="${i}">${i+1}. ${name}</option>`).join('')+'</select></label></div><h2 id="studio-step-title" tabindex="-1"></h2><p id="studio-step-description"></p><progress id="studio-step-progress" max="6" value="1" aria-label="제작 순서"></progress>';form.before(guide);
  const nav=document.createElement('nav');nav.className='studio-step-nav';nav.setAttribute('role','tablist');nav.setAttribute('aria-label','청첩장 제작 단계');nav.innerHTML=steps.map((name,i)=>`<button type="button" role="tab" id="studio-tab-${i}" data-studio-step="${i}" aria-controls="studio-step-${i}" aria-selected="${i===activeStep}"><span>${String(i+1).padStart(2,'0')}</span>${name}</button>`).join('');form.before(nav);
  const actions=document.createElement('div');actions.className='studio-wizard-actions';actions.innerHTML='<button type="button" class="secondary" data-studio-wizard="previous">이전</button><button type="button" data-studio-wizard="next">다음</button>';form.append(actions);
  const switcher=document.createElement('div');switcher.className='studio-view-switch';switcher.setAttribute('aria-label','작업 화면 선택');switcher.innerHTML=`<button type="button" data-studio-view="edit">${icon('pencil-line')}편집</button><button type="button" data-studio-view="preview">${icon('eye')}미리보기</button>`;document.querySelector('.studio-layout').before(switcher);
  const toolbar=document.createElement('div');toolbar.className='studio-toolbar';toolbar.innerHTML=`<div class="toolbar-inner"><p id="studio-toolbar-status" role="status" aria-live="polite"></p><div class="toolbar-actions"><button type="button" id="studio-view-toggle" class="secondary mobile-only" data-studio-action="toggle-view">${icon('eye')}미리보기</button><button type="button" class="toolbar-save" data-act="save">${icon('save')}저장하기</button></div></div>`;document.querySelector('#studio').append(toolbar);
  showStep(activeStep);showView(view);
}
document.addEventListener('click',event=>{
  const step=event.target.closest('[data-studio-step]');if(step){showStep(Number(step.dataset.studioStep),true);showView('edit');alignView();return;}
  const wizard=event.target.closest('[data-studio-wizard]');if(wizard){if(wizard.dataset.studioWizard==='previous'){showStep(activeStep-1,true);alignView();}else if(activeStep===steps.length-1)changeView('preview');else if(canAdvance()){showStep(activeStep+1,true);alignView();}return;}
  const switcher=event.target.closest('button[data-studio-view]');if(switcher){changeView(switcher.dataset.studioView);return;}
  if(event.target.closest('[data-studio-action="toggle-view"]'))changeView(view==='edit'?'preview':'edit');
  if(event.target.closest('[data-design-action=select]')&&matchMedia('(max-width:760px)').matches){showStep(3);showView('edit');document.querySelector('.selected-layer')?.scrollIntoView({block:'start',behavior:'auto'});}
});
document.addEventListener('change',event=>{if(event.target.id==='studio-step-jump'){showStep(Number(event.target.value),true);showView('edit');alignView();}});
document.addEventListener('input',event=>{if(event.target.closest('#editor-form')&&typeof event.target.setCustomValidity==='function')event.target.setCustomValidity('');});
document.addEventListener('keydown',event=>{const button=event.target.closest('[data-studio-step]');if(!button||!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();const index=Number(button.dataset.studioStep),next=event.key==='Home'?0:event.key==='End'?steps.length-1:(index+(event.key==='ArrowRight'?1:-1)+steps.length)%steps.length;showStep(next);showView('edit');document.getElementById('studio-tab-'+next)?.focus({preventScroll:true});});
