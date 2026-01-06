<template>
  <div class="title-bar">
    <div class="title-drag-region">
      <div class="app-icon">
        <img src="/LINE_New_App_Icon.png" alt="Icon" />
      </div>
      <div class="app-title">LINE Message Bridge</div>
    </div>
    <div class="window-controls">
      <div class="control-btn minimize" @click="minimize">
        <el-icon><Minus /></el-icon>
      </div>
      <div class="control-btn maximize" @click="maximize">
        <el-icon><FullScreen /></el-icon>
      </div>
      <div class="control-btn close" @click="close">
        <el-icon><Close /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Minus, FullScreen, Close } from '@element-plus/icons-vue'

const minimize = () => {
  window.ipcRenderer.send('window:minimize')
}

const maximize = () => {
  window.ipcRenderer.send('window:maximize')
}

const close = () => {
  window.ipcRenderer.send('window:close')
}
</script>

<style scoped>
.title-bar {
  height: 42px;
  background: var(--tech-bg-dark);
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--tech-border);
  user-select: none;
  z-index: 9999;
  flex-shrink: 0; /* Critical to prevent shrinking */
}

.title-drag-region {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: center;
  padding-left: 15px;
  -webkit-app-region: drag;
}

.app-icon {
  width: 24px;
  height: 24px;
  margin-right: 10px;
  display: flex;
  align-items: center;
}

.app-icon img {
  width: 100%;
  height: 100%;
}

.app-title {
  font-size: 14px;
  color: var(--tech-text-main);
  font-weight: bold;
  letter-spacing: 1px;
}

.window-controls {
  display: flex;
  height: 100%;
  -webkit-app-region: no-drag;
}

.control-btn {
  width: 50px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--tech-text-dim);
  cursor: pointer;
  transition: all 0.2s;
}

.control-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--tech-text-main);
}

.control-btn.close:hover {
  background: #e81123;
  color: white;
}

.el-icon {
  font-size: 16px;
}
</style>
