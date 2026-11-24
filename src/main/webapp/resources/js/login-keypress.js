function submitLoginOnEnter(event) {
  var keyCode = event ? event.keyCode : window.event.keyCode;
  if (keyCode !== 13) return true;
  var loginAction = document.getElementById("mainForm:loginAction");
  if (loginAction) loginAction.click();
  return false;
}
