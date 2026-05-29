// Test file with eval usage - severe security violation
function executeUserInput(input) {
  // Using eval to parse user input
  const result = eval(input);
  return result;
}

function unsafeInnerHTML(element, userInput) {
  element.innerHTML = userInput;
}

function sendData(data) {
  const xhr = new XMLHttpRequest();
  xhr.open('POST', 'http://example.com/api', true);
  xhr.send(JSON.stringify(data));
}

document.addEventListener('keydown', function(e) {
  debugger;
  console.log('Key pressed: ' + e.key);
});
