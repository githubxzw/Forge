<template>
  <div>
    <div class="toolbar" style="justify-content: space-between">
      <span>
        <el-tag v-if="status" :type="statusType" size="small" style="margin-right: 8px">{{ statusText }}</el-tag>
        <span class="muted">实时日志（历史自动重放 + 实时推送）</span>
      </span>
      <el-button size="small" :icon="Download" @click="download">下载完整日志</el-button>
    </div>
    <div ref="box" class="log-box">{{ text }}<span v-if="running" class="cursor">▌</span></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { Download } from '@element-plus/icons-vue'

const props = defineProps<{ kind: 'build' | 'deploy'; id: string; logUrl?: string }>()
const text = ref('')
const ended = ref(false)
const connected = ref(false)
const box = ref<HTMLElement>()

let ws: WebSocket | null = null
let closedByEnd = false

const status = computed(() => (ended.value ? 'ended' : connected.value ? 'live' : ''))
const statusType = computed(() => (ended.value ? 'info' : 'success'))
const statusText = computed(() => (ended.value ? '已结束' : '实时推送中'))
const running = computed(() => connected.value && !ended.value)

function append(line: string) {
  if (line === '__FORGE_LOG_END__') {
    ended.value = true
    closedByEnd = true
    ws?.close()
    return
  }
  text.value += line + '\n'
  nextTick(() => {
    if (box.value) box.value.scrollTop = box.value.scrollHeight
  })
}

function connect() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const token = localStorage.getItem('forge_token') || ''
  ws = new WebSocket(`${proto}://${location.host}/ws/logs/${props.kind}/${props.id}?token=${token}`)
  ws.onopen = () => (connected.value = true)
  ws.onmessage = e => append(String(e.data))
  ws.onclose = () => {
    connected.value = false
    if (!closedByEnd && !ended.value) {
      // 服务端可能还没产出日志（任务排队中），2s 后重连
      setTimeout(() => { if (!ended.value) connect() }, 2000)
    }
  }
  ws.onerror = () => ws?.close()
}

function download() {
  if (props.logUrl) window.open(props.logUrl, '_blank')
}

onMounted(connect)
onUnmounted(() => { ws?.close() })
</script>

<style scoped>
.cursor { animation: blink 1s infinite; color: #7ec8e3; }
@keyframes blink { 50% { opacity: 0; } }
</style>
