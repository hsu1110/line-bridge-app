// ---------------------------------------------------------
// LINE Message Bridge - GAS Helper (Auto-Register Version)
// ---------------------------------------------------------

// 設定
var SHEET_ID = ""; // 如果留空，會自動尋找或建立一個名為 "LineBotData" 的試算表
var SHEET_NAME_CONTACTS = "Contacts";
var SHEET_NAME_CONFIG = "BotConfig";

// ---------------------------------------------------------
// 1. 處理 LINE Webhook (doPost)
// ---------------------------------------------------------
function doPost(e) {
  var msg = JSON.parse(e.postData.contents);
  var events = msg.events;
  var destination = msg.destination; // Bot User ID

  if (!events || events.length === 0) {
    return ContentService.createTextOutput("OK");
  }

  // 取得該 Bot 的 Token (若無則自動註冊)
  var token = getAndRegisterBotToken(destination);

  if (!token) {
    // Token 未設定 (可能是剛自動註冊的新 Bot)
    // 無法回覆訊息，直接結束
    return ContentService.createTextOutput(
      "New Bot Registered. Please set Token in Sheet."
    );
  }

  for (var i = 0; i < events.length; i++) {
    var event = events[i];
    var replyToken = event.replyToken;
    var source = event.source;

    // A. 處理加好友 (Follow)
    if (event.type === "follow") {
      var userId = source.userId;
      var profile = getProfile(userId, token);
      var displayName = profile ? profile.displayName : "Unknown User";

      saveToSheet(displayName, userId, "user", destination);
      replyMessage(
        replyToken,
        "🎉 歡迎 " +
          displayName +
          "！\n\n我是您的訊息通知小幫手 🤖\n已自動為您完成綁定。\n\n💡 若想更改顯示名稱，請輸入：\n「系統綁定：您的暱稱」",
        token
      );
    }

    // B. 處理加入群組 (Join)
    if (event.type === "join") {
      var groupId = source.groupId;
      var summary = getGroupSummary(groupId, token);
      var groupName = summary ? summary.groupName : "Unknown Group";

      saveToSheet(groupName, groupId, "group", destination);
      replyMessage(
        replyToken,
        "大家好 👋 我是通知小幫手！\n\n已綁定本群組：" +
          groupName +
          "\n\n💡 若需更改群組顯示名稱，請輸入：\n「系統綁定：新名稱」",
        token
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
          saveToSheet(customName, id, type, destination);
          replyMessage(
            replyToken,
            "✅ 更新成功！\n名稱：" + customName + "\nID：" + id,
            token
          );
        }
      }
    }

    // D. 處理封鎖/刪除好友 (Unfollow)
    if (event.type === "unfollow") {
      var userId = source.userId;
      deleteFromSheet(userId, destination);
    }

    // E. 處理離開群組 (Leave)
    if (event.type === "leave") {
      var id = source.groupId || source.roomId;
      deleteFromSheet(id, destination);
    }
  }

  return ContentService.createTextOutput("OK");
}

// ---------------------------------------------------------
// 2. 提供 Electron 匯入 API (doGet)
// ---------------------------------------------------------
function doGet(e) {
  var type = e.parameter.type;
  var data;

  if (type === "config") {
    data = readConfigFromSheet();
  } else {
    data = readFromSheet();
  }

  return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(
    ContentService.MimeType.JSON
  );
}

// ---------------------------------------------------------
// LINE API Helpers
// ---------------------------------------------------------

function getAndRegisterBotToken(botId) {
  var sheet = getSheet(SHEET_NAME_CONFIG);
  var data = sheet.getDataRange().getValues();

  // 1. 嘗試尋找現有 Bot
  for (var i = 1; i < data.length; i++) {
    if (data[i][2] === botId) {
      var token = data[i][3];
      if (!token || token === "PENDING_INPUT") {
        return null; // 有 ID 但沒 Token
      }
      return token;
    }
  }

  // 2. 找不到 -> 自動註冊
  var timestamp = new Date();
  // 格式: [Timestamp, BotName, BotID, ChannelAccessToken, ChannelSecret]
  sheet.appendRow([
    timestamp,
    "(New Bot)",
    botId,
    "PENDING_INPUT",
    "PENDING_INPUT",
  ]);

  return null;
}

function getProfile(userId, token) {
  try {
    var url = "https://api.line.me/v2/bot/profile/" + userId;
    var response = UrlFetchApp.fetch(url, {
      headers: { Authorization: "Bearer " + token },
    });
    return JSON.parse(response.getContentText());
  } catch (e) {
    return null;
  }
}

function getGroupSummary(groupId, token) {
  try {
    var url = "https://api.line.me/v2/bot/group/" + groupId + "/summary";
    var response = UrlFetchApp.fetch(url, {
      headers: { Authorization: "Bearer " + token },
    });
    return JSON.parse(response.getContentText());
  } catch (e) {
    return null;
  }
}

function replyMessage(replyToken, message, token) {
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
        Authorization: "Bearer " + token,
      },
      method: "post",
      payload: JSON.stringify(payload),
    });
  } catch (e) {
    // ignore
  }
}

// ---------------------------------------------------------
// Sheet Helpers
// ---------------------------------------------------------

function getSheet(sheetName) {
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

  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    if (sheetName === SHEET_NAME_CONTACTS) {
      sheet.appendRow(["Timestamp", "Name", "ID", "Type", "LinkedBotID"]);
    } else if (sheetName === SHEET_NAME_CONFIG) {
      sheet.appendRow([
        "Timestamp",
        "BotName",
        "BotID",
        "ChannelAccessToken",
        "ChannelSecret",
      ]);
    }
  }
  return sheet;
}

function saveToSheet(name, id, type, linkedBotId) {
  var sheet = getSheet(SHEET_NAME_CONTACTS);
  var timestamp = new Date();
  var data = sheet.getDataRange().getValues();

  // Update existing
  for (var i = 1; i < data.length; i++) {
    if (data[i][2] === id) {
      sheet.getRange(i + 1, 2).setValue(name); // Update Name
      sheet.getRange(i + 1, 1).setValue(timestamp);

      var currentLinked = data[i][4] ? String(data[i][4]).split(",") : [];
      if (linkedBotId && currentLinked.indexOf(linkedBotId) === -1) {
        currentLinked.push(linkedBotId);
        sheet.getRange(i + 1, 5).setValue(currentLinked.join(","));
      }
      return;
    }
  }
  // Append new
  sheet.appendRow([timestamp, name, id, type, linkedBotId]);
}

function deleteFromSheet(id, botId) {
  var sheet = getSheet(SHEET_NAME_CONTACTS);
  var data = sheet.getDataRange().getValues();

  for (var i = data.length - 1; i >= 1; i--) {
    if (data[i][2] === id) {
      var currentLinked = data[i][4] ? String(data[i][4]).split(",") : [];
      var index = currentLinked.indexOf(botId);

      if (index > -1) {
        // Remove only this bot ID
        currentLinked.splice(index, 1);

        if (currentLinked.length === 0) {
          // If no bots left, delete the row
          sheet.deleteRow(i + 1);
        } else {
          // Update the list
          sheet.getRange(i + 1, 5).setValue(currentLinked.join(","));
        }
      }
      return;
    }
  }
}

function readFromSheet() {
  var sheet = getSheet(SHEET_NAME_CONTACTS);
  var data = sheet.getDataRange().getValues();
  var result = [];
  for (var i = 1; i < data.length; i++) {
    var linkedBotIds = data[i][4] ? String(data[i][4]).split(",") : [];
    result.push({
      name: data[i][1],
      id: data[i][2],
      type: data[i][3],
      linkedBotIds: linkedBotIds,
    });
  }
  return result;
}

function readConfigFromSheet() {
  var sheet = getSheet(SHEET_NAME_CONFIG);
  var data = sheet.getDataRange().getValues();
  var result = [];
  // Skip header (row 1)
  for (var i = 1; i < data.length; i++) {
    // [Timestamp, BotName, BotID, ChannelAccessToken, ChannelSecret]
    result.push({
      name: data[i][1],
      lineBotId: data[i][2], // BotID in sheet -> lineBotId in app
      channelAccessToken: data[i][3],
      secret: data[i][4],
    });
  }
  return result;
}
