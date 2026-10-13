import {newspaperPages} from '/newspaper-pages.js';
const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let page=0,reading=false;
function readablePages(data,n){
 const p=text=>`<p>${escape(text)}</p>`,h=text=>`<h2>${escape(text)}</h2>`,photo=(src,alt)=>src?`<img class="np-readable-photo" data-act="newspaper-photo" role="button" tabindex="0" src="${escape(src)}" alt="${escape(alt)}">`:'';
 const masthead=`<div class="np-readable-masthead">Newspaper · ${escape(n.issue)}</div>`;
 return [
  masthead+'<h2 class="np-readable-title">Wedding Times</h2>'+p([data.groom,data.bride].filter(Boolean).join(' · '))+h(n.headline)+photo(data.cover,'두 사람의 대표 사진')+h('신랑 '+(data.groom||''))+p(n.groomProfile)+h('신부 '+(data.bride||''))+p(n.brideProfile)+h('양가 가족 구성원을 소개합니다!')+p(n.familyNames.join('\n')),
  masthead+h('우리의 연애 일대기')+n.memories.map((text,i)=>`<figure>${photo(data.gallery?.[i],'추억 사진 '+(i+1))}<figcaption>${escape(text)}</figcaption></figure>`).join(''),
  masthead+h('왜 결혼을 결심하게 되었나?')+h((data.groom||'신랑')+'의 이야기')+p(n.groomAnswer)+h((data.bride||'신부')+'의 이야기')+p(n.brideAnswer)+h('예식 준비는 문제없이 진행중!')+p(n.preparation)+h(data.venue||'예식장')+p([data.address,data.dateTime?.replace('T',' ')].filter(Boolean).join('\n'))+h('앞으로의 계획')+p(n.futurePlans),
  masthead+h('양가 부모님께 드리는 말씀')+p(n.familyLetter)+p('Thank you for being part of our story')
 ];
}
export const blankNewspaper=()=>({issue:'결혼 소식 특별호',headline:'꽃다발 같은 식을 올릴\n신랑 신부를 소개합니다!',groomProfile:'MBTI:\n성격 TMI:\n직업:\n최애 음식:',brideProfile:'MBTI:\n성격 TMI:\n직업:\n최애 음식:',groomAnswer:'함께 있을 때 가장 나다운 사람이 됩니다.',brideAnswer:'서로에게 든든한 편이 되어주고 싶습니다.',preparation:'소중한 분들을 모실 예식을 준비하고 있습니다.',futurePlans:'서로를 응원하며 즐겁게 살아가겠습니다.',familyLetter:'저희에게 사랑을 주는 법과 받는 법을 알려주신 부모님께 감사드립니다.\n\n앞으로도 서로에게 든든한 편이 되어주며 행복하게 살겠습니다.\n\n저희 두 사람의 새로운 시작을 함께해 주세요.',familyNames:['신랑 어머니','신랑 아버지','신랑','신랑 가족','신부 어머니','신부 아버지','신부','신부 가족'],memories:['처음 만난 날','첫 데이트','함께한 여행','우리의 기념일','함께 쌓은 시간','그리고 우리의 다음 이야기']});
export function newspaperControls(data){
 const n=data.newspaper||blankNewspaper();
 const field=(key,label,max,rows=3)=>`<label>${label}<textarea data-newspaper-field="${key}" maxlength="${max}" rows="${rows}">${escape(n[key])}</textarea></label>`;
 return `<section id="newspaper-controls" ${data.template==='NEWSPAPER'?'':'hidden'}><h3>신문에 담을 우리의 이야기</h3><p class="hint">원본의 네 페이지를 채워보세요. 사진은 ‘사진’ 단계의 대표 사진과 갤러리 사진을 사용합니다. 지면의 작은 글은 ‘크게 읽기’로 볼 수 있어요.</p><label>발행호 이름<input data-newspaper-field="issue" maxlength="30" value="${escape(n.issue)}"></label>${field('headline','1면 · 메인 기사 제목',100)}<div class="field-row">${field('groomProfile','신랑 소개',200,5)}${field('brideProfile','신부 소개',200,5)}</div><details class="newspaper-editor-detail"><summary>1면 · 가족 이름 편집</summary>${n.familyNames.map((name,i)=>`<label>${i<4?'신랑측':'신부측'} 가족 ${i%4+1}<input data-newspaper-family="${i}" maxlength="30" value="${escape(name)}"></label>`).join('')}</details><details class="newspaper-editor-detail"><summary>2면 · 추억 사진 설명</summary>${n.memories.map((text,i)=>`<label>추억 ${i+1}<input data-newspaper-memory="${i}" maxlength="100" value="${escape(text)}"></label>`).join('')}</details>${field('groomAnswer','3면 · 신랑의 인터뷰',200)}${field('brideAnswer','3면 · 신부의 인터뷰',200)}${field('preparation','예식 준비 이야기',300)}${field('futurePlans','앞으로의 계획',200)}${field('familyLetter','4면 · 양가 부모님께 드리는 말씀',1000,8)}<p class="hint">짧은 글은 지면에, 긴 글은 크게 읽기 화면에 모두 표시됩니다. 신문형은 원본 지면 배치를 사용하며 자유 편집 표지는 기존 디자인에 적용됩니다.</p></section>`;
}
export function collectNewspaper(data){
 const section=document.querySelector('#newspaper-controls');if(!section||data.template!=='NEWSPAPER')return;
 const n=data.newspaper??=blankNewspaper();
 section.querySelectorAll('[data-newspaper-field]').forEach(input=>n[input.dataset.newspaperField]=input.value.trim());
 section.querySelectorAll('[data-newspaper-family]').forEach(input=>n.familyNames[Number(input.dataset.newspaperFamily)]=input.value.trim());
 section.querySelectorAll('[data-newspaper-memory]').forEach(input=>n.memories[Number(input.dataset.newspaperMemory)]=input.value.trim());
}
export function syncNewspaperControls(data){const section=document.querySelector('#newspaper-controls');if(section)section.hidden=data.template!=='NEWSPAPER';}
export function newspaperMarkup(data,reference=false){
 const n=data.newspaper||blankNewspaper(),t=document.createElement('template');t.innerHTML=newspaperPages.join('');
 const set=(id,value)=>{const node=t.content.querySelector(`[data-node-id="${id}"]`);if(node){node.textContent=value;node.classList.add('np-editable');}};
 if(!reference){
  const eventDate=data.dateTime?new Date(data.dateTime+'+09:00'):null;
  const date=eventDate?new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Seoul',year:'numeric',month:'short',day:'numeric',weekday:'short'}).format(eventDate):'Our Wedding Day';
  ['4001:205','4001:468','4001:542','4001:581'].forEach(id=>set(id,date));
  ['4001:206','4001:469','4001:543','4001:582'].forEach(id=>set(id,n.issue));
  set('4001:208',data.groom||'신랑');set('4001:209',data.bride||'신부');
  set('4001:212',(eventDate?new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'long',day:'numeric'}).format(eventDate)+'\n':'')+n.headline);
  set('4001:220','신랑 '+(data.groom||''));set('4001:223','신부 '+(data.bride||''));
  set('4001:216',n.groomProfile);set('4001:641',n.brideProfile);
  ['4001:227','4001:231','4001:235','4001:237','4001:646','4001:233','4001:651','4001:653'].forEach((id,i)=>set(id,n.familyNames[i]||''));
  set('4001:221','[ '+(data.groom||'신랑')+' 가족 ]');set('4001:222','[ '+(data.bride||'신부')+' 가족 ]');
  ['4001:491','4001:675','4001:680','4001:694'].forEach(id=>set(id,''));
  ['4001:665','4001:676','4001:690','4001:681','4001:695','4001:699'].forEach((id,i)=>set(id,n.memories[i]||''));
  set('4001:547',`${data.groom||'신랑'} · ${data.bride||'신부'},\n왜 결혼을 결심하게 되었나?\n단독 인터뷰 [사랑의 짝대기]`);
  set('4001:549',(data.groom||'신랑')+'의 이야기');set('4001:550',(data.bride||'신부')+'의 이야기');
  set('4001:560',n.groomAnswer);set('4001:558',n.brideAnswer);set('4001:706',n.preparation);set('4001:563','소중한 분들을 모실 준비를 하고 있어요.');
  set('4001:570','[ 예식장 : '+(data.venue||'예식장')+' ]');set('4001:707',[data.address,data.dateTime?.replace('T',' '),data.venue].filter(Boolean).join('\n'));
  set('4001:565',n.futurePlans);set('4001:600',n.familyLetter);
  const photos=[data.cover,...(data.gallery||[])].filter(Boolean);
  t.content.querySelectorAll('img[src^="/newspaper-assets/"][src$=".png"]').forEach((img,i)=>{if(photos.length){if(/(62c19|fc86a|18a0e|59e04)\.png$/.test(img.src))img.classList.add('np-circle-photo');img.src=photos[i%photos.length];img.alt='우리의 사진';img.classList.add('np-user-photo');img.style.pointerEvents='auto';img.tabIndex=0;img.setAttribute('role','button');img.dataset.act='newspaper-photo';}});
 }
 const names=['1면 · 소개','2면 · 추억','3면 · 인터뷰','4면 · 감사'];
 const readable=readablePages(data,n);
 return `<section class="newspaper-feature ${reading?'np-reading':''}" aria-label="우리의 결혼 신문"><div class="np-navigation"><nav aria-label="청첩장 지면">${names.map((name,i)=>`<button type="button" data-act="newspaper-page" data-index="${i}" aria-current="${i===page?'page':'false'}">${name}</button>`).join('')}</nav><button type="button" class="np-reading-toggle" data-act="newspaper-reading" aria-pressed="${reading}">${reading?'원본 지면 보기':'크게 읽기'}</button></div>${[...t.content.children].map((sheet,i)=>`<section class="np-page" aria-label="${names[i]}" ${i===page?'':'hidden'}>${reading?'<article class="np-readable">'+readable[i]+'</article>':sheet.outerHTML}</section>`).join('')}<div class="np-pager"><button type="button" data-act="newspaper-page" data-index="${Math.max(0,page-1)}" ${page===0?'disabled':''}>이전 지면</button><span>${page+1} / 4</span><button type="button" data-act="newspaper-page" data-index="${Math.min(3,page+1)}" ${page===3?'disabled':''}>다음 지면</button></div></section>`;
}
export function newspaperAction(button){if(button.dataset.act==='newspaper-page'){page=Math.max(0,Math.min(3,Number(button.dataset.index)));return true;}if(button.dataset.act==='newspaper-reading'){reading=!reading;return true;}return false;}
const sizes=new ResizeObserver(entries=>entries.forEach(({target,contentRect})=>target.style.setProperty('--np-scale',String(contentRect.width/595))));
export function fitNewspaper(){sizes.disconnect();document.querySelectorAll('.np-page').forEach(node=>sizes.observe(node));}
