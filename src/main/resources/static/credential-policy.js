export function usernameError(value){return /^[a-z0-9_-]{5,20}$/.test(value)?'':'아이디는 영문 소문자·숫자·밑줄(_)·하이픈(-)으로 5~20자 입력해 주세요.';}
export function passwordError(value){const length=[...value].length;return length>=8&&length<=64?'':'비밀번호는 8~64자로 입력해 주세요.';}
