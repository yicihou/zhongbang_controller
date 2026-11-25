<template>
  <div class="vouchers">
    <el-image
      v-for="url in images"
      :key="url"
      class="thumb"
      :src="resolveFileUrl(url)"
      :preview-src-list="images.map(resolveFileUrl)"
      fit="cover"
      preview-teleported
    />
    <span v-if="texts.length">{{ texts.join('；') }}</span>
    <span v-if="images.length === 0 && texts.length === 0" class="muted">—</span>
  </div>
</template>

<script setup lang="ts">
/**
 * 收款凭证。图片显示成小图，点开看大图；以前手填的单号仍然按文字显示。
 * 相对地址 /uploads/... 会补上接口根地址，因为图片在后端，不在 Vite 页面自己的域名上。
 */
import { computed } from 'vue'
import { API_BASE } from '@/config'
import { isImageUrl } from '@/domain/image'

const props = defineProps<{ urls?: string[] }>()

const images = computed(() => (props.urls ?? []).filter((item) => isImageUrl(item)))
const texts = computed(() => (props.urls ?? []).map((item) => item.trim()).filter((item) => item && !isImageUrl(item)))

function resolveFileUrl(url: string): string {
  if (url.startsWith('data:') || url.startsWith('http://') || url.startsWith('https://')) return url
  if (url.startsWith('/')) return `${API_BASE}${url}`
  return url
}
</script>

<style scoped>
.vouchers {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.thumb {
  width: 56px;
  height: 56px;
  border-radius: 4px;
  border: 1px solid var(--zb-line, #e5e7eb);
  cursor: pointer;
}
</style>
