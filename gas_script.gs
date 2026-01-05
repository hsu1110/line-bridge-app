// ---------------------------------------------------------
// LINE Message Bridge - GAS Helper
// 功能 1: 接收 LINE Webhook，處理「系統綁定」指令
// 功能 2: 提供 API (doGet) 讓 Electron 匯入名單
// ---------------------------------------------------------

// ⚠️ 請先至「專案設定」->「指令碼屬性」新增屬性：
// 屬性名稱: CHANNEL_ACCESS_TOKEN
// 屬性值: 您的 LINE Channel Access Token
var scriptProperties = PropertiesService.getScriptProperties();
var CHANNEL_ACCESS_TOKEN = scriptProperties.getProperty("CHANNEL_ACCESS_TOKEN");

var SHEET_ID = ""; // 如果留空，會自動尋找或建立一個名為 "LineBotData" 的試算表
var SHEET_NAME = "Contacts";

// ---------------------------------------------------------
// 1. 處理 LINE Webhook (doPost)
// ---------------------------------------------------------
function doPost(e) {
  if (!CHANNEL_ACCESS_TOKEN) {
    // 如果沒設定 Token，回傳錯誤 Log (雖然 LINE 看不到)
    console.error("尚未設定 CHANNEL_ACCESS_TOKEN");
    return ContentService.createTextOutput("Error: Token not set");
  }

  var msg = JSON.parse(e.postData.contents);
  var event = msg.events[0];

  // 處理加入群組/聊天室事件 (join)
  if (event.type === "join") {
    var replyToken = event.replyToken;
    var welcomeMsg =
      "感謝您將本機器人加入好友或群組！🤖\n\n若要讓此聊天室接收來自系統的通知，請輸入以下指令進行綁定：\n\n系統綁定：識別名稱\n\n範例： 若您是個人使用，請輸入： 系統綁定：王小明\n\n若這是部門群組，請輸入： 系統綁定：IT部門群組\n\n輸入後請等待機器人回覆「✅ 綁定成功」，即完成設定！";
    replyMessage(replyToken, welcomeMsg);
    return ContentService.createTextOutput("OK");
  }

  // 只處理文字訊息
  if (!event || event.type !== "message" || event.message.type !== "text") {
    return ContentService.createTextOutput("OK");
  }

  var text = event.message.text.trim();
  var replyToken = event.replyToken;
  var source = event.source;

  // 檢查指令: "系統綁定：[名稱]" 或 "System Bind:[Name]"
  var regex = /^(系統綁定|System Bind)[:：]\s*(.+)$/i;
  var match = text.match(regex);

  if (match) {
    var customName = match[2].trim();
    var id = source.groupId || source.roomId || source.userId;
    var type = source.type;

    if (!id) return;

    saveToSheet(customName, id, type);
    replyMessage(
      replyToken,
      "✅ 綁定成功！\n名稱：" + customName + "\nID：" + id
    );
  }

  return ContentService.createTextOutput("OK");
}

// ---------------------------------------------------------
// 2. 提供 Electron 匯入 API (doGet)
// ---------------------------------------------------------
function doGet(e) {
  var data = readFromSheet();
  return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(
    ContentService.MimeType.JSON
  );
}

// ---------------------------------------------------------
// 輔助函式
// ---------------------------------------------------------

function replyMessage(replyToken, message) {
  var url = "https://api.line.me/v2/bot/message/reply";
  var payload = {
    replyToken: replyToken,
    messages: [
      {
        type: "text",
        text: message,
      },
    ],
  };

  UrlFetchApp.fetch(url, {
    headers: {
      "Content-Type": "application/json; charset=UTF-8",
      Authorization: "Bearer " + CHANNEL_ACCESS_TOKEN,
    },
    method: "post",
    payload: JSON.stringify(payload),
  });
}

function getSheet() {
  var ss;
  if (SHEET_ID) {
    ss = SpreadsheetApp.openById(SHEET_ID);
  } else {
    try {
      ss = SpreadsheetApp.getActiveSpreadsheet();
    } catch (e) {}
  }

  if (!ss) {
    // 嘗試搜尋檔名
    var files = DriveApp.getFilesByName("LineBotData");
    if (files.hasNext()) {
      ss = SpreadsheetApp.open(files.next());
    } else {
      // 建立新檔案
      ss = SpreadsheetApp.create("LineBotData");
    }
  }

  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(["Timestamp", "Name", "ID", "Type"]);
  }
  return sheet;
}

function saveToSheet(name, id, type) {
  var sheet = getSheet();
  var timestamp = new Date();
  var data = sheet.getDataRange().getValues();

  for (var i = 1; i < data.length; i++) {
    if (data[i][2] === id) {
      sheet.getRange(i + 1, 2).setValue(name);
      sheet.getRange(i + 1, 1).setValue(timestamp);
      return;
    }
  }
  sheet.appendRow([timestamp, name, id, type]);
}

function readFromSheet() {
  var sheet = getSheet();
  var data = sheet.getDataRange().getValues();
  var result = [];
  for (var i = 1; i < data.length; i++) {
    result.push({
      name: data[i][1],
      id: data[i][2],
      type: data[i][3],
    });
  }
  return result;
}
