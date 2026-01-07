// ---------------------------------------------------------
// LINE Message Bridge - GAS Helper (Smart Binding Version)
// 功能 1: 接收 LINE Webhook
//   - Follow/Join: 自動抓取名稱並綁定
//   - Message: 處理「系統綁定」指令 (改名用)
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
    console.error("尚未設定 CHANNEL_ACCESS_TOKEN");
    return ContentService.createTextOutput("Error: Token not set");
  }

  var msg = JSON.parse(e.postData.contents);
  var events = msg.events;

  if (!events || events.length === 0) {
    return ContentService.createTextOutput("OK");
  }

  for (var i = 0; i < events.length; i++) {
    var event = events[i];
    var replyToken = event.replyToken;
    var source = event.source;

    // A. 處理加好友 (Follow) -> 自動抓取個人資料
    if (event.type === "follow") {
      var userId = source.userId;
      var profile = getProfile(userId);
      var displayName = profile ? profile.displayName : "Unknown User";

      saveToSheet(displayName, userId, "user");
      replyMessage(
        replyToken,
        "🎉 歡迎 " +
          displayName +
          "！\n\n我是您的訊息通知小幫手 🤖\n已自動為您完成綁定。\n\n💡 若想更改顯示名稱，請輸入：\n「系統綁定：您的暱稱」"
      );
    }

    // B. 處理加入群組 (Join) -> 自動抓取群組資料
    if (event.type === "join") {
      var groupId = source.groupId;
      var summary = getGroupSummary(groupId);
      var groupName = summary ? summary.groupName : "Unknown Group";

      saveToSheet(groupName, groupId, "group");
      replyMessage(
        replyToken,
        "大家好 👋 我是通知小幫手！\n\n已綁定本群組：" +
          groupName +
          "\n\n💡 若需更改群組顯示名稱，請輸入：\n「系統綁定：新名稱」"
      );
    }

    // C. 處理文字訊息 (改名指令)
    if (event.type === "message" && event.message.type === "text") {
      var text = event.message.text.trim();
      var regex = /^(系統綁定|System Bind)[:：]\s*(.+)$/i;
      var match = text.match(regex);

      if (match) {
        var customName = match[2].trim();
        var id = source.groupId || source.roomId || source.userId;
        var type = source.type;

        if (id) {
          saveToSheet(customName, id, type);
          replyMessage(
            replyToken,
            "✅ 更新成功！\n名稱：" + customName + "\nID：" + id
          );
        }
      }
    }

    // D. 處理封鎖/刪除好友 (Unfollow)
    if (event.type === "unfollow") {
      var userId = source.userId;
      deleteFromSheet(userId);
    }

    // E. 處理離開群組 (Leave)
    if (event.type === "leave") {
      var id = source.groupId || source.roomId;
      deleteFromSheet(id);
    }
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
// LINE API Helpers
// ---------------------------------------------------------

function getProfile(userId) {
  try {
    var url = "https://api.line.me/v2/bot/profile/" + userId;
    var response = UrlFetchApp.fetch(url, {
      headers: { Authorization: "Bearer " + CHANNEL_ACCESS_TOKEN },
    });
    return JSON.parse(response.getContentText());
  } catch (e) {
    console.error("Get Profile Error: " + e);
    return null;
  }
}

function getGroupSummary(groupId) {
  try {
    var url = "https://api.line.me/v2/bot/group/" + groupId + "/summary";
    var response = UrlFetchApp.fetch(url, {
      headers: { Authorization: "Bearer " + CHANNEL_ACCESS_TOKEN },
    });
    return JSON.parse(response.getContentText());
  } catch (e) {
    console.error("Get Group Summary Error: " + e);
    return null;
  }
}

function replyMessage(replyToken, message) {
  if (!replyToken) return;
  var url = "https://api.line.me/v2/bot/message/reply";
  var payload = {
    replyToken: replyToken,
    messages: [{ type: "text", text: message }],
  };

  try {
    UrlFetchApp.fetch(url, {
      headers: {
        "Content-Type": "application/json; charset=UTF-8",
        Authorization: "Bearer " + CHANNEL_ACCESS_TOKEN,
      },
      method: "post",
      payload: JSON.stringify(payload),
    });
  } catch (e) {
    console.error("Reply Error: " + e);
  }
}

// ---------------------------------------------------------
// Sheet Helpers
// ---------------------------------------------------------

function getSheet() {
  var ss;
  if (SHEET_ID) {
    try {
      ss = SpreadsheetApp.openById(SHEET_ID);
    } catch (e) {}
  } else {
    try {
      ss = SpreadsheetApp.getActiveSpreadsheet();
    } catch (e) {}
  }

  if (!ss) {
    var files = DriveApp.getFilesByName("LineBotData");
    if (files.hasNext()) {
      ss = SpreadsheetApp.open(files.next());
    } else {
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

  // Update existing
  for (var i = 1; i < data.length; i++) {
    if (data[i][2] === id) {
      sheet.getRange(i + 1, 2).setValue(name); // Update Name
      sheet.getRange(i + 1, 1).setValue(timestamp);
      return;
    }
  }
  // Append new
  sheet.appendRow([timestamp, name, id, type]);
}

function deleteFromSheet(id) {
  var sheet = getSheet();
  var data = sheet.getDataRange().getValues();

  // Iterate backwards to avoid index shifting issues
  for (var i = data.length - 1; i >= 1; i--) {
    if (data[i][2] === id) {
      // Column C is ID (index 2)
      sheet.deleteRow(i + 1);
      // Assuming ID is unique, we can return after deleting.
      // If you suspect duplicates, remove the return statement.
      return;
    }
  }
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
