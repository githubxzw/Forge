<template>
  <span class="s-tag" :class="cls"><i class="dot"></i>{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()
const map: Record<string, { label: string; cls: string }> = {
  PENDING: { label: '排队中', cls: 's-tag--info' },
  RUNNING: { label: '进行中', cls: 's-tag--running' },
  SUCCESS: { label: '成功', cls: 's-tag--success' },
  FAILED: { label: '失败', cls: 's-tag--failed' },
  CANCELLED: { label: '已取消', cls: 's-tag--info' }
}
const label = computed(() => map[props.status]?.label || props.status)
const cls = computed(() => map[props.status]?.cls || 's-tag--info')
</script>
