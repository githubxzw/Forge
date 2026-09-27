<template>
  <div>
    <div class="toolbar" style="justify-content: space-between">
      <span class="muted">历史自动重放 · 实时推送</span>
      <el-button size="small" :icon="Download" @click="download">下载完整日志</el-button>
    </div>
    <div class="term">
      <div class="term-head">
        <div class="dots"><i></i><i></i><i></i></div>
        <div class="term-title">{{ kind === 'build' ? 'build' : 'deploy' }} · {{ id.slice(0, 8) }}</div>
        <span class="s-tag" :class="ended ? 's-tag--info' : 's-tag--running'">
          <i class="dot"></i>{{ ended ? '已结束' : '实时推送中' }}
        </span>
      </div>
      <div ref="box" class="term-body"><span
        v-for="(l, i) in lines" :key="i" :class="lineClass(l)">{{ l + '\n' }}</span><span v-if="running" class="term-cursor">▌</span></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { Download } from '@element-plus/icons-vue'

const props = defineProps<{ kind: 'build' | 'deploy'; id: string; logUrl?: string }>()
const lines = ref<string[]>([])
const ended = ref(false)
const connected = ref(false)
const box = ref<HTMLElement>()

let ws: WebSocket | null = null
let closedByEnd = false

const running = computed(() => connected.value && !ended.value)

function lineClass(l: string) {
  if (l.startsWith('[forge] 构建成功') || l.startsWith('[forge] 部署成功')) return 'ok'
  if (l.startsWith('[forge] 构建失败') || l.startsWith('[forge] 部署失败')) return 'err'
  if (l.startsWith('[forge]') || l.startsWith('__FORGE')) return 'sys'
  return ''
}

function append(line: string) {
  if (line === '__FORGE_LOG_END__') {
    ended.value = true
    closedByEnd = true
    ws?.close()
    return
  }
  lines.value.push(line)
  if (lines.value.length > 5000) lines.value.splice(0, 1000) // 防超长内存膨胀
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
