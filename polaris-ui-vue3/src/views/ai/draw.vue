<template>
  <div class="draw-workshop">
    <!-- 左侧：能力选择 -->
    <aside class="draw-side">
      <h3 class="side-title">生成能力</h3>
      <div v-for="grp in capabilities" :key="grp.group" class="cap-group">
        <div class="cap-group-title">{{ grp.group }}</div>
        <div class="cap-list">
          <button
            v-for="cap in grp.items"
            :key="cap.code"
            :class="['cap-btn', { active: currentMode === cap.code }]"
            @click="selectMode(cap)"
          >{{ cap.label }}</button>
        </div>
      </div>
    </aside>

    <!-- 中间：参数与操作 -->
    <section class="draw-main">
      <div class="mode-banner">
        <span class="mode-name">{{ activeCap.label }}</span>
        <span class="mode-hint">{{ modeHint }}</span>
      </div>

      <!-- 源图上传 -->
      <div v-if="activeCap.needsSource" class="form-block">
        <label class="form-label">
          源图{{ activeCap.multi ? '（可多张）' : '' }}
        </label>
        <div class="src-uploader-container">
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :before-upload="beforeImageUpload"
            :on-success="onSourceUploaded"
            :multiple="activeCap.multi"
            accept="image/*"
            class="src-uploader"
          >
            <el-button type="primary" plain :disabled="!activeCap.multi && sourceImages.length >= 1">
              上传源图
            </el-button>
          </el-upload>
        </div>
        <div class="thumb-row">
          <div v-for="(url, idx) in sourceImages" :key="idx" class="thumb">
            <img :src="resolveUrl(url)" />
            <span class="thumb-del" @click="removeSource(idx)">×</span>
          </div>
        </div>
      </div>

      <!-- 颜色图谱面板：仅"指令改图"模式显示，前端做像素级颜色精确替换，规避模型自由发挥颜色不准的问题 -->
      <div v-if="activeCap.code === 'image_edit' && sourceImages.length" class="form-block">
        <label class="form-label">
          颜色图谱（自动识别）
          <el-button size="small" link @click="refreshColorMap">刷新</el-button>
        </label>
        <div v-if="colorMap.length" class="color-map">
          <div
            v-for="(c, idx) in colorMap"
            :key="idx"
            :class="['color-chip', { active: targetColorIdx === idx }]"
            :style="{ background: c.hex }"
            @click="targetColorIdx = idx"
            :title="c.name + ' ' + c.hex"
          >
            <span class="color-rank">第{{ ['一','二','三','四','五','六','七','八'][idx] }}种</span>
            <span class="color-name">{{ c.name }}</span>
          </div>
        </div>
        <div v-else class="color-map-empty">正在分析源图颜色...</div>

        <label class="form-label" style="margin-top:14px;">操作模式</label>
        <el-radio-group v-model="colorOpMode" size="small">
          <el-radio-button label="unify_to">全部统一为选中色</el-radio-button>
          <el-radio-button label="replace_with">把选中色替换为其他</el-radio-button>
          <el-radio-button label="off">关闭（按 prompt 改图）</el-radio-button>
        </el-radio-group>

        <div v-if="colorOpMode === 'replace_with'" class="form-block" style="margin-top:10px;">
          <label class="form-label">目标颜色（中文/英文/十六进制）</label>
          <el-input v-model="replaceTargetColor" placeholder="如：红色、blue、#ff0000"></el-input>
        </div>

        <div class="color-map-tip">
          <el-icon><info-filled></info-filled></el-icon>
          <span>启用颜色图谱后，前端将像素级精确替换颜色，再由模型仅做低强度精修，避免"颜色不准"。</span>
        </div>
      </div>

      <!-- 遮罩画布 -->
      <div v-if="activeCap.needsMask && sourceImages.length" class="form-block">
        <label class="form-label">
          遮罩涂抹（在需要处理的区域涂白）
          <el-button size="small" @click="clearMask">清除</el-button>
          <span class="brush-ctrl">
            笔刷
            <el-slider v-model="brushSize" :min="5" :max="80" style="width:120px;display:inline-block;vertical-align:middle;" />
          </span>
        </label>
        <div class="mask-stage">
          <img ref="maskBg" :src="resolveUrl(sourceImages[0])" class="mask-bg" @load="onMaskImgLoad" />
          <canvas
            ref="maskCanvas"
            class="mask-canvas"
            @pointerdown="startPaint"
            @pointermove="paint"
            @pointerup="endPaint"
            @pointerleave="endPaint"
          ></canvas>
        </div>
      </div>

      <!-- 提示词 -->
      <div class="form-block">
        <label class="form-label">提示词</label>
        <el-input v-model="prompt" type="textarea" :rows="3" placeholder="描述你想要的画面..." />
      </div>
      <div class="form-block">
        <label class="form-label">负向提示词（可选）</label>
        <el-input v-model="negativePrompt" type="textarea" :rows="2" placeholder="不希望出现的元素..." />
      </div>

      <!-- 尺寸 / 数量 -->
      <div class="form-row">
        <div class="form-block">
          <label class="form-label">尺寸</label>
          <el-select v-model="size" style="width:160px;">
            <el-option v-for="s in sizeOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="form-block">
          <label class="form-label">数量</label>
          <el-input-number v-model="n" :min="1" :max="4" />
        </div>
      </div>

      <el-button
        type="primary"
        size="large"
        :loading="submitting"
        class="generate-btn"
        @click="handleGenerate"
      >开始生成</el-button>
    </section>

    <!-- 右侧：结果 -->
    <aside class="draw-results">
      <h3 class="side-title">生成结果</h3>
      <div v-if="!results.length" class="results-empty">还没有生成记录</div>
      <div v-for="task in results" :key="task.taskId" class="result-card">
        <div class="result-head">
          <span class="result-mode">{{ modeLabel(task.generationMode) }}</span>
          <span :class="['result-status', 'st-' + task.status]">{{ statusText(task.status) }}</span>
        </div>
        <div class="result-body">
          <div v-if="task.status === '0'" class="result-loading">
            <el-icon class="is-loading"><loading></loading></el-icon> 生成中...
          </div>
          <img
            v-else-if="task.status === '1' && task.imageUrl"
            :src="resolveUrl(task.imageUrl)"
            class="result-img"
            @click="preview(task.imageUrl)"
          />
          <div v-else-if="task.status === '2'" class="result-error">{{ task.errorMsg || '生成失败' }}</div>
        </div>
        <div class="result-prompt">{{ task.prompt }}</div>
      </div>
    </aside>

    <el-image-viewer v-if="viewerUrl" :url-list="[viewerUrl]" @close="viewerUrl = ''"></el-image-viewer>
  </div>
</template>

<script>
import {getToken} from '@/utils/auth'
import {generateImage, getImageTaskStatus, listImageCapabilities} from '@/api/ai/image'
import {InfoFilled, Loading} from '@element-plus/icons-vue'
import {ElImageViewer} from 'element-plus'

const CAPABILITIES = [
  { group: '生成类', items: [
    { code: 'text_to_image', label: '文生图', needsSource: false, needsMask: false, multi: false },
    { code: 'image_to_image', label: '图生图', needsSource: true, needsMask: false, multi: false },
    { code: 'multi_image', label: '多图生成', needsSource: true, needsMask: false, multi: true }
  ]},
  { group: '编辑类', items: [
    { code: 'image_edit', label: '指令改图', needsSource: true, needsMask: false, multi: false },
    { code: 'inpainting', label: '局部重绘', needsSource: true, needsMask: true, multi: false },
    { code: 'object_removal', label: '消除', needsSource: true, needsMask: true, multi: false },
    { code: 'outpainting', label: '扩图', needsSource: true, needsMask: false, multi: false },
    { code: 'background_replacement', label: '换背景', needsSource: true, needsMask: false, multi: false }
  ]},
  { group: '增强类', items: [
    { code: 'upscale', label: '高清放大', needsSource: true, needsMask: false, multi: false },
    { code: 'restoration', label: '照片修复', needsSource: true, needsMask: false, multi: false }
  ]}
]

export default {
  name: 'AiDraw',
  components: { Loading, InfoFilled, ElImageViewer },
  data() {
    return {
      supportedModes: null,
      currentMode: 'text_to_image',
      prompt: '',
      negativePrompt: '',
      size: '1024x1024',
      n: 1,
      sizeOptions: ['512x512', '768x768', '1024x1024', '1024x768', '768x1024'],
      sourceImages: [],
      brushSize: 30,
      painting: false,
      maskDirty: false,
      submitting: false,
      results: [],
      polls: {},
      viewerUrl: '',
      uploadUrl: (import.meta.env.VITE_APP_BASE_API || '') + '/common/upload',
      uploadHeaders: { Authorization: 'Bearer ' + getToken() },
      // 颜色图谱相关状态（指令改图模式专用）
      colorMap: [],
      targetColorIdx: -1,
      colorOpMode: 'off',
      replaceTargetColor: ''
    }
  },
  computed: {
    capabilities() {
      if (!this.supportedModes) return CAPABILITIES
      return CAPABILITIES
        .map(g => ({ group: g.group, items: g.items.filter(i => this.supportedModes.includes(i.code)) }))
        .filter(g => g.items.length)
    },
    activeCap() {
      for (const g of CAPABILITIES) {
        const f = g.items.find(i => i.code === this.currentMode)
        if (f) return f
      }
      return CAPABILITIES[0].items[0]
    },
    modeHint() {
      const c = this.activeCap
      if (c.needsMask) return '需要源图 + 涂抹遮罩'
      if (c.needsSource) return c.multi ? '需要一张或多张源图' : '需要一张源图'
      return '纯文字描述即可生成'
    }
  },
  created() {
    this.loadCapabilities()
  },
  beforeUnmount() {
    Object.values(this.polls).forEach(t => clearInterval(t))
  },
  methods: {
    async loadCapabilities() {
      try {
        const res = await listImageCapabilities()
        if (res.code === 200 && Array.isArray(res.data)) {
          this.supportedModes = res.data
          const flat = this.capabilities.flatMap(g => g.items.map(i => i.code))
          if (flat.length && !flat.includes(this.currentMode)) {
            this.currentMode = flat[0]
          }
        }
      } catch (e) {
        // 接口异常时保持全部能力可选，不阻断页面
      }
    },
    selectMode(cap) {
      this.currentMode = cap.code
      if (!cap.multi && this.sourceImages.length > 1) {
        this.sourceImages = this.sourceImages.slice(0, 1)
      }
      this.maskDirty = false
      // 切换能力时重置颜色图谱
      this.colorMap = []
      this.targetColorIdx = -1
      if (cap.code === 'image_edit' && this.sourceImages.length) {
        this.$nextTick(() => this.refreshColorMap())
      }
    },
    resolveUrl(url) {
      if (!url) return ''
      return url.startsWith('http') ? url : this.uploadUrl.replace('/common/upload', '') + url
    },
    beforeImageUpload(file) {
      const ok = file.type.startsWith('image/')
      if (!ok) this.$message.error('只能上传图片文件')
      return ok
    },
    onSourceUploaded(res) {
      if (res.code === 200) {
        if (this.activeCap.multi) this.sourceImages.push(res.url)
        else this.sourceImages = [res.url]
        // 仅在指令改图模式下自动刷新颜色图谱
        if (this.activeCap.code === 'image_edit') {
          this.$nextTick(() => this.refreshColorMap())
        }
      } else {
        this.$message.error(res.msg || '上传失败')
      }
    },
    removeSource(idx) {
      this.sourceImages.splice(idx, 1)
      this.maskDirty = false
      if (this.activeCap.code === 'image_edit') {
        this.colorMap = []
        this.targetColorIdx = -1
        if (this.sourceImages.length) {
          this.$nextTick(() => this.refreshColorMap())
        }
      }
    },
    onMaskImgLoad() {
      const img = this.$refs.maskBg
      const cvs = this.$refs.maskCanvas
      if (!img || !cvs) return
      cvs.width = img.naturalWidth
      cvs.height = img.naturalHeight
      cvs.style.width = img.clientWidth + 'px'
      cvs.style.height = img.clientHeight + 'px'
      cvs.getContext('2d').clearRect(0, 0, cvs.width, cvs.height)
      this.maskDirty = false
    },
    canvasPoint(e) {
      const cvs = this.$refs.maskCanvas
      const rect = cvs.getBoundingClientRect()
      return {
        x: (e.clientX - rect.left) * (cvs.width / rect.width),
        y: (e.clientY - rect.top) * (cvs.height / rect.height)
      }
    },
    startPaint(e) {
      this.painting = true
      const ctx = this.$refs.maskCanvas.getContext('2d')
      const p = this.canvasPoint(e)
      ctx.beginPath()
      ctx.moveTo(p.x, p.y)
    },
    paint(e) {
      if (!this.painting) return
      const cvs = this.$refs.maskCanvas
      const ctx = cvs.getContext('2d')
      const p = this.canvasPoint(e)
      ctx.strokeStyle = 'rgba(255,255,255,0.9)'
      ctx.lineWidth = this.brushSize * (cvs.width / cvs.clientWidth)
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.lineTo(p.x, p.y)
      ctx.stroke()
      this.maskDirty = true
    },
    endPaint() {
      this.painting = false
    },
    clearMask() {
      const cvs = this.$refs.maskCanvas
      if (cvs) cvs.getContext('2d').clearRect(0, 0, cvs.width, cvs.height)
      this.maskDirty = false
    },
    buildMaskBlob() {
      return new Promise(resolve => {
        const paint = this.$refs.maskCanvas
        const out = document.createElement('canvas')
        out.width = paint.width
        out.height = paint.height
        const ctx = out.getContext('2d')
        ctx.fillStyle = '#000000'
        ctx.fillRect(0, 0, out.width, out.height)
        ctx.drawImage(paint, 0, 0)
        out.toBlob(b => resolve(b), 'image/png')
      })
    },
    async uploadBlob(blob, filename) {
      const fd = new FormData()
      fd.append('file', blob, filename)
      const resp = await fetch(this.uploadUrl, {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + getToken() },
        body: fd
      })
      const json = await resp.json()
      if (json.code === 200) return json.url
      throw new Error(json.msg || '遮罩上传失败')
    },
    async handleGenerate() {
      const cap = this.activeCap
      if (!this.prompt.trim()) {
        this.$message.warning('请输入提示词')
        return
      }
      if (cap.needsSource && !this.sourceImages.length) {
        this.$message.warning('该能力需要上传源图')
        return
      }
      let maskUrl = null
      if (cap.needsMask) {
        if (!this.maskDirty) {
          this.$message.warning('请在源图上涂抹遮罩区域')
          return
        }
        try {
          const blob = await this.buildMaskBlob()
          maskUrl = await this.uploadBlob(blob, 'mask.png')
        } catch (e) {
          this.$message.error(e.message || '遮罩处理失败')
          return
        }
      }
      this.submitting = true
      try {
        // 指令改图模式 + 启用颜色图谱 → 前端做像素级精确替换（颜色100%可控），后端仅做低强度精修
        let processedSources = cap.needsSource ? this.sourceImages : null
        let extra = null
        let effectivePrompt = this.prompt
        if (cap.code === 'image_edit' && this.colorOpMode !== 'off'
            && processedSources && processedSources.length && this.colorMap.length) {
          try {
            const replaced = await this.applyColorMapEdit(processedSources[0])
            if (replaced) {
              const uploadedUrl = await this.uploadDataUrl(replaced.dataUrl, 'color_edited.png')
              processedSources = [uploadedUrl]
              // 标记后端：前端已做像素级精确替换，仅做低强度精修（避免模型自由发挥）
              extra = { pixelExact: true }
              // 颜色指令已由前端精确完成，prompt 清空以避免模型再次改色
              effectivePrompt = ''
              this.$message.success('已像素级精确替换颜色，模型仅做画质精修')
            }
          } catch (e) {
            console.error('[Draw] 像素级颜色替换失败，回退到原 prompt 流程', e)
            this.$message.warning('颜色替换失败，回退到普通改图模式：' + (e.message || ''))
          }
        }
        const res = await generateImage({
          prompt: effectivePrompt,
          negativePrompt: this.negativePrompt || null,
          generationMode: cap.code,
          sourceImages: processedSources,
          maskImage: maskUrl,
          size: this.size,
          n: this.n,
          extra
        })
        if (res.code === 200) {
          const task = res.data
          this.results.unshift(task)
          this.startPolling(task.taskId)
        } else {
          this.$message.error(res.msg || '发起失败')
        }
      } catch (e) {
        this.$message.error('发起绘图失败')
      } finally {
        this.submitting = false
      }
    },
    startPolling(taskId) {
      let count = 0
      const max = 180
      const poll = async () => {
        count++
        if (count > max) { this.stopPolling(taskId); return }
        try {
          const res = await getImageTaskStatus(taskId)
          if (res.code === 200 && res.data) {
            const idx = this.results.findIndex(t => t.taskId === taskId)
            if (idx > -1) this.results.splice(idx, 1, res.data)
            if (res.data.status === '1' || res.data.status === '2') this.stopPolling(taskId)
          }
        } catch (e) { /* 忽略单次失败 */ }
      }
      poll()
      this.polls[taskId] = setInterval(poll, 2000)
    },
    stopPolling(taskId) {
      if (this.polls[taskId]) {
        clearInterval(this.polls[taskId])
        delete this.polls[taskId]
      }
    },
    modeLabel(code) {
      for (const g of CAPABILITIES) {
        const f = g.items.find(i => i.code === code)
        if (f) return f.label
      }
      return code
    },
    statusText(s) {
      return s === '1' ? '成功' : s === '2' ? '失败' : '生成中'
    },
    preview(url) {
      this.viewerUrl = this.resolveUrl(url)
    },

    // ============================================================
    //  颜色图谱 / 像素级精确替换 相关方法（指令改图专用）
    // ============================================================

    /**
     * 从源图提取主要颜色（降采样 + 量化 + KMeans 简易聚类）
     * 返回 [{ name, hex, rgb: [r,g,b] }, ...]，按像素数降序
     */
    async refreshColorMap() {
      if (!this.sourceImages.length || this.activeCap.code !== 'image_edit') return
      this.colorMap = []
      this.targetColorIdx = -1
      const url = this.sourceImages[0]
      try {
        const img = await this.loadImage(this.resolveUrl(url))
        const cvs = document.createElement('canvas')
        const scale = Math.min(1, 200 / Math.max(img.naturalWidth, img.naturalHeight))
        cvs.width = Math.max(1, Math.round(img.naturalWidth * scale))
        cvs.height = Math.max(1, Math.round(img.naturalHeight * scale))
        const ctx = cvs.getContext('2d')
        ctx.drawImage(img, 0, 0, cvs.width, cvs.height)
        const { data } = ctx.getImageData(0, 0, cvs.width, cvs.height)

        // 颜色量化到 4-bit-per-channel（4096 桶）
        const buckets = new Map()
        for (let i = 0; i < data.length; i += 4) {
          if (data[i + 3] < 128) continue
          const r = data[i], g = data[i + 1], b = data[i + 2]
          // 过滤白底与近黑
          if (r > 240 && g > 240 && b > 240) continue
          const qr = r >> 4, qg = g >> 4, qb = b >> 4
          const key = (qr << 8) | (qg << 4) | qb
          const cur = buckets.get(key) || { r: 0, g: 0, b: 0, n: 0 }
          cur.r += r; cur.g += g; cur.b += b; cur.n += 1
          buckets.set(key, cur)
        }
        const topBuckets = [...buckets.values()].sort((a, b) => b.n - a.n).slice(0, 32)
        if (!topBuckets.length) return

        // 简易 KMeans：先取 top8 做种子，迭代 5 次
        let centroids = topBuckets.slice(0, 8).map(b => ({
          r: b.r / b.n, g: b.g / b.n, b: b.b / b.n,
          n: 0, sumR: 0, sumG: 0, sumB: 0
        }))
        for (let iter = 0; iter < 5; iter++) {
          centroids.forEach(c => { c.n = 0; c.sumR = 0; c.sumG = 0; c.sumB = 0 })
          for (const b of topBuckets) {
            const cr = b.r / b.n, cg = b.g / b.n, cb = b.b / b.n
            let best = -1, bestDist = Infinity
            for (let i = 0; i < centroids.length; i++) {
              const c = centroids[i]
              const dr = cr - c.r, dg = cg - c.g, db = cb - c.b
              const dist = dr * dr + dg * dg + db * db
              if (dist < bestDist) { bestDist = dist; best = i }
            }
            centroids[best].n += b.n
            centroids[best].sumR += b.r
            centroids[best].sumG += b.g
            centroids[best].sumB += b.b
          }
          centroids.forEach(c => {
            if (c.n > 0) {
              c.r = c.sumR / c.n; c.g = c.sumG / c.n; c.b = c.sumB / c.n
            }
          })
        }
        centroids.sort((a, b) => b.n - a.n)

        // 同色相合并：相邻色簇若 hue 距离 < 18° 则合并
        const merged = []
        for (const c of centroids.slice(0, 8)) {
          const r = Math.round(c.r), g = Math.round(c.g), b = Math.round(c.b)
          const name = this.rgbToColorName(r, g, b)
          if (merged.length && merged[merged.length - 1].name === name) continue
          merged.push({ name, hex: this.rgbToHex(r, g, b), rgb: [r, g, b] })
          if (merged.length >= 6) break
        }
        this.colorMap = merged
        this.targetColorIdx = merged.length ? 0 : -1
      } catch (e) {
        console.error('[Draw] 提取颜色图谱失败', e)
        this.$message.warning('颜色图谱提取失败：' + (e.message || ''))
      }
    },

    loadImage(src) {
      return new Promise((resolve, reject) => {
        const img = new Image()
        img.crossOrigin = 'anonymous'
        img.onload = () => resolve(img)
        img.onerror = (e) => reject(new Error('图片加载失败'))
        img.src = src
      })
    },

    /**
     * RGB 转可读颜色名（基础 12 色 + 灰度判定）
     */
    rgbToColorName(r, g, b) {
      const max = Math.max(r, g, b), min = Math.min(r, g, b)
      const delta = max - min
      if (delta < 20) {
        if (max < 60) return '深灰'
        if (max < 130) return '灰色'
        if (max < 200) return '浅灰'
        return '近白'
      }
      let h
      if (max === r) h = ((g - b) / delta) % 6
      else if (max === g) h = (b - r) / delta + 2
      else h = (r - g) / delta + 4
      h = Math.round(h * 60); if (h < 0) h += 360
      const s = delta / max, v = max / 255
      const base = (hue) => {
        if (hue < 15 || hue >= 345) return '红'
        if (hue < 35) return '橙'
        if (hue < 65) return '黄'
        if (hue < 100) return '黄绿'
        if (hue < 160) return '绿'
        if (hue < 200) return '青'
        if (hue < 250) return '蓝'
        if (hue < 290) return '紫'
        if (hue < 335) return '粉'
        return '红'
      }
      const name = base(h)
      if (v < 0.35) return '深' + name
      if (s < 0.4) return name + '灰'
      return name + '色'
    },

    rgbToHex(r, g, b) {
      const pad = (n) => Math.max(0, Math.min(255, n)).toString(16).padStart(2, '0')
      return '#' + pad(r) + pad(g) + pad(b)
    },

    /**
     * 解析用户输入的"目标颜色"，支持中文/英文/十六进制/rgb()
     */
    parseUserColor(input) {
      if (!input) return null
      const s = input.trim()
      if (/^#?[0-9a-fA-F]{6}$/.test(s)) {
        const hex = s.startsWith('#') ? s : '#' + s
        return {
          r: parseInt(hex.slice(1, 3), 16),
          g: parseInt(hex.slice(3, 5), 16),
          b: parseInt(hex.slice(5, 7), 16)
        }
      }
      const rgbMatch = s.match(/rgb\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)/i)
      if (rgbMatch) {
        return { r: +rgbMatch[1], g: +rgbMatch[2], b: +rgbMatch[3] }
      }
      const colorMap = {
        '红': [255, 0, 0], '红色': [255, 0, 0], '深红': [139, 0, 0],
        '橙': [255, 165, 0], '橙色': [255, 165, 0],
        '黄': [255, 255, 0], '黄色': [255, 255, 0],
        '绿': [0, 255, 0], '绿色': [0, 255, 0], '深绿': [0, 100, 0], '浅绿': [144, 238, 144],
        '青': [0, 255, 255], '青色': [0, 255, 255],
        '蓝': [0, 0, 255], '蓝色': [0, 0, 255], '深蓝': [0, 0, 139], '浅蓝': [173, 216, 230],
        '紫': [128, 0, 128], '紫色': [128, 0, 128], '深紫': [75, 0, 130],
        '粉': [255, 192, 203], '粉色': [255, 192, 203], '粉红': [255, 192, 203],
        '黑': [0, 0, 0], '黑色': [0, 0, 0], '白': [255, 255, 255], '白色': [255, 255, 255],
        '灰': [128, 128, 128], '灰色': [128, 128, 128],
        'red': [255, 0, 0], 'orange': [255, 165, 0], 'yellow': [255, 255, 0],
        'green': [0, 128, 0], 'cyan': [0, 255, 255],
        'blue': [0, 0, 255], 'purple': [128, 0, 128], 'pink': [255, 192, 203],
        'black': [0, 0, 0], 'white': [255, 255, 255], 'gray': [128, 128, 128], 'grey': [128, 128, 128]
      }
      const lower = s.toLowerCase()
      if (colorMap[s] || colorMap[lower]) {
        const [r, g, b] = colorMap[s] || colorMap[lower]
        return { r, g, b }
      }
      return null
    },

    /**
     * 像素级替换颜色，返回 { dataUrl, replaced }。
     *
     * @param {string} srcUrl 源图 URL
     * @param {[r,g,b]} targetRgb 要替换的源色（被替换色）
     * @param {[r,g,b]} newRgb 目标色（替换为）
     * @param {number} threshold 色差阈值（0-441，平方空间），越大越宽松
     */
    async replaceColorPixels(srcUrl, targetRgb, newRgb, threshold = 60) {
      const img = await this.loadImage(this.resolveUrl(srcUrl))
      const cvs = document.createElement('canvas')
      cvs.width = img.naturalWidth
      cvs.height = img.naturalHeight
      const ctx = cvs.getContext('2d')
      ctx.drawImage(img, 0, 0)
      const imageData = ctx.getImageData(0, 0, cvs.width, cvs.height)
      const d = imageData.data
      const t2 = threshold * threshold
      let replaced = 0
      for (let i = 0; i < d.length; i += 4) {
        if (d[i + 3] < 128) continue
        const dr = d[i] - targetRgb[0]
        const dg = d[i + 1] - targetRgb[1]
        const db = d[i + 2] - targetRgb[2]
        if (dr * dr + dg * dg + db * db <= t2) {
          d[i] = newRgb[0]
          d[i + 1] = newRgb[1]
          d[i + 2] = newRgb[2]
          replaced++
        }
      }
      ctx.putImageData(imageData, 0, 0)
      return { dataUrl: cvs.toDataURL('image/png'), replaced }
    },

    /**
     * 把 dataURL 上传到 /common/upload，返回正式 URL
     */
    async uploadDataUrl(dataUrl, filename) {
      const blob = await (await fetch(dataUrl)).blob()
      const fd = new FormData()
      fd.append('file', blob, filename)
      const resp = await fetch(this.uploadUrl, {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + getToken() },
        body: fd
      })
      const json = await resp.json()
      if (json.code === 200) return json.url
      throw new Error(json.msg || '上传失败')
    },

    /**
     * 根据当前 colorMap + colorOpMode + targetColorIdx + replaceTargetColor，
     * 在前端对源图做像素级颜色替换，返回 dataURL；返回 null 表示未启用或无需替换。
     */
    async applyColorMapEdit(srcUrl) {
      if (this.colorOpMode === 'off') return null
      if (!this.colorMap.length || this.targetColorIdx < 0) {
        throw new Error('请先选择要操作的颜色')
      }
      const selected = this.colorMap[this.targetColorIdx]
      let targetRgb, newRgb
      if (this.colorOpMode === 'unify_to') {
        // 全部统一为选中色：把源图中除"白底/近白"外的所有彩色像素，全部替换为选中色
        // 实现思路：遍历 colorMap 中除 selected 之外的所有色簇，依次替换为 selected
        targetRgb = null  // 标记下面走"遍历替换"分支
        const acc = { dataUrl: null, replaced: 0 }
        let currentUrl = srcUrl
        for (let i = 0; i < this.colorMap.length; i++) {
          if (i === this.targetColorIdx) continue
          const from = this.colorMap[i].rgb
          const result = await this.replaceColorPixels(currentUrl, from, selected.rgb, 50)
          currentUrl = result.dataUrl
          acc.replaced += result.replaced
          if (result.replaced === 0) break
        }
        return { dataUrl: currentUrl, replaced: acc.replaced }
      } else if (this.colorOpMode === 'replace_with') {
        const parsed = this.parseUserColor(this.replaceTargetColor)
        if (!parsed) throw new Error('目标颜色无效，请输入颜色名或十六进制')
        targetRgb = selected.rgb
        newRgb = [parsed.r, parsed.g, parsed.b]
      } else {
        return null
      }
      if (!targetRgb) return null  // 防御
      return await this.replaceColorPixels(srcUrl, targetRgb, newRgb, 50)
    }
  }
}
</script>

<style lang="scss" scoped>
.draw-workshop {
  /* 基础与布局 */
  display: flex;
  height: 100%;
  gap: 20px;
  padding: 20px;
  box-sizing: border-box;
  transition: background-color 0.3s ease;

  /* 浅色模式变量 */
  --bg-app: #f8fafc;
  --bg-card: #ffffff;
  --border-color: #e2e8f0;
  --text-primary: #1e293b;
  --text-regular: #475569;
  --text-muted: #94a3b8;
  --primary-color: #3b82f6;
  --primary-gradient: linear-gradient(135deg, #3b82f6 0%, #6366f1 100%);
  --primary-hover-gradient: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  --shadow-sm: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05);
  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -4px rgba(0, 0, 0, 0.05);
  --btn-active-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  --scrollbar-thumb: #cbd5e1;
  --scrollbar-track: transparent;
  --bg-input: #f8fafc;
  --border-input: #cbd5e1;
  --border-input-hover: #94a3b8;
  --focus-ring: rgba(99, 102, 241, 0.15);
  --btn-upload-bg: rgba(59, 130, 246, 0.08);
  --btn-upload-border: #3b82f6;
  --btn-upload-text: #3b82f6;

  background: var(--bg-app);

  /* 深色模式变量 */
  &.dark,
  .dark &,
  .theme-dark & {
    --bg-app: #090d16;
    --bg-card: rgba(17, 24, 39, 0.7);
    --border-color: rgba(255, 255, 255, 0.08);
    --text-primary: #f8fafc;
    --text-regular: #cbd5e1;
    --text-muted: #64748b;
    --primary-color: #6366f1;
    --primary-gradient: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
    --primary-hover-gradient: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
    --shadow-sm: 0 1px 3px 0 rgba(0, 0, 0, 0.3);
    --shadow-md: 0 10px 15px -3px rgba(0, 0, 0, 0.3), 0 4px 6px -2px rgba(0, 0, 0, 0.3);
    --shadow-lg: 0 20px 25px -5px rgba(0, 0, 0, 0.4), 0 10px 10px -5px rgba(0, 0, 0, 0.4);
    --btn-active-shadow: 0 4px 20px rgba(139, 92, 246, 0.4);
    --scrollbar-thumb: #334155;
    --scrollbar-track: transparent;
    --bg-input: rgba(15, 23, 42, 0.5);
    --border-input: rgba(255, 255, 255, 0.16);
    --border-input-hover: rgba(255, 255, 255, 0.3);
    --focus-ring: rgba(139, 92, 246, 0.25);
    --btn-upload-bg: rgba(255, 255, 255, 0.05);
    --btn-upload-border: rgba(255, 255, 255, 0.4);
    --btn-upload-text: #ffffff;

    background: var(--bg-app);
  }
}

.draw-side {
  width: 210px;
  background: var(--bg-card);
  border-radius: 12px;
  padding: 20px 16px;
  overflow-y: auto;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(12px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 20px;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: var(--scrollbar-track);
  }
  &::-webkit-scrollbar-thumb {
    background: var(--scrollbar-thumb);
    border-radius: 3px;
  }
}

.side-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 8px;

  &::before {
    content: '';
    display: inline-block;
    width: 4px;
    height: 16px;
    background: var(--primary-gradient);
    border-radius: 2px;
  }
}

.cap-group {
  margin: 0;
}

.cap-group-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.cap-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cap-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  color: var(--text-regular);
  box-shadow: var(--shadow-sm);

  &:hover {
    border-color: var(--primary-color);
    color: var(--primary-color);
    transform: translateY(-1px);
    box-shadow: var(--shadow-md);
  }

  &.active {
    background: var(--primary-gradient);
    border-color: transparent;
    color: #fff;
    font-weight: 600;
    box-shadow: var(--btn-active-shadow);
    
    &:hover {
      transform: translateY(-1px);
      box-shadow: var(--btn-active-shadow);
    }
  }
}

.draw-main {
  flex: 1;
  background: var(--bg-card);
  border-radius: 12px;
  padding: 24px;
  overflow-y: auto;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(12px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: var(--scrollbar-track);
  }
  &::-webkit-scrollbar-thumb {
    background: var(--scrollbar-thumb);
    border-radius: 3px;
  }
}

.mode-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--border-color);
}

.mode-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.mode-hint {
  font-size: 13px;
  color: var(--text-muted);
}

.form-block {
  margin-bottom: 20px;
}

.form-row {
  display: flex;
  gap: 24px;
}

.form-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-regular);
  margin-bottom: 8px;
}

.brush-ctrl {
  margin-left: 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-regular);
  display: flex;
  align-items: center;
  gap: 8px;
}

.thumb-row {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.thumb {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  transition: all 0.3s ease;

  &:hover {
    transform: scale(1.05);
    border-color: var(--primary-color);
    box-shadow: var(--shadow-md);
  }
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-del {
  position: absolute;
  top: 4px;
  right: 4px;
  cursor: pointer;
  color: #fff;
  background: rgba(239, 68, 68, 0.85);
  border-radius: 50%;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: all 0.2s ease;

  &:hover {
    background: #ef4444;
    transform: scale(1.1);
  }
}

.mask-stage {
  position: relative;
  display: inline-block;
  max-width: 100%;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-color);
}

.mask-bg {
  max-width: 512px;
  max-height: 512px;
  display: block;
  border-radius: 8px;
}

.mask-canvas {
  position: absolute;
  left: 0;
  top: 0;
  cursor: crosshair;
  touch-action: none;
}

.generate-btn {
  width: 100%;
  margin-top: 12px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border: none !important;
  border-radius: 8px !important;
  background: var(--primary-gradient) !important;
  color: #ffffff !important;
  box-shadow: var(--btn-active-shadow);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;

  &:hover:not(.is-loading) {
    background: var(--primary-hover-gradient) !important;
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.45);
  }

  &:active:not(.is-loading) {
    transform: translateY(1px);
  }
}

.draw-results {
  width: 320px;
  background: var(--bg-card);
  border-radius: 12px;
  padding: 20px 16px;
  overflow-y: auto;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(12px);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 20px;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: var(--scrollbar-track);
  }
  &::-webkit-scrollbar-thumb {
    background: var(--scrollbar-thumb);
    border-radius: 3px;
  }
}

.results-empty {
  color: var(--text-muted);
  font-size: 14px;
  text-align: center;
  margin-top: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;

  &::before {
    content: '🎨';
    font-size: 32px;
    opacity: 0.6;
  }
}

.result-card {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 14px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
    border-color: var(--primary-color);
  }
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-mode {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-regular);
}

.result-status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  
  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: currentColor;
  }
}

.st-0 {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  
  &::before {
    animation: pulse 1.5s infinite;
  }
}

.st-1 {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.st-2 {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

@keyframes pulse {
  0% { opacity: 0.4; }
  50% { opacity: 1; }
  100% { opacity: 0.4; }
}

.result-img {
  width: 100%;
  border-radius: 8px;
  cursor: zoom-in;
  transition: transform 0.3s ease;

  &:hover {
    transform: scale(1.02);
  }
}

.result-loading {
  color: var(--primary-color);
  font-size: 13px;
  font-weight: 500;
  text-align: center;
  padding: 30px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.result-error {
  color: #ef4444;
  font-size: 12px;
  text-align: center;
  padding: 15px 0;
}

.result-prompt {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  
  &:hover {
    color: var(--text-regular);
  }
}

/* 美化上传按钮 */
.src-uploader-container {
  display: inline-block;
  
  :deep(.el-button.el-button--primary) {
    height: 38px;
    padding: 8px 18px;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 600;
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    
    border: 1.5px solid var(--btn-upload-border) !important;
    color: var(--btn-upload-text) !important;
    background-color: var(--btn-upload-bg) !important;
    
    &:hover:not(.is-disabled) {
      background: var(--primary-gradient) !important;
      color: #ffffff !important;
      border-color: transparent !important;
      transform: translateY(-1px);
      box-shadow: var(--btn-active-shadow);
    }
    
    &:active:not(.is-disabled) {
      transform: translateY(1px);
    }

    &.is-disabled {
      border-color: var(--border-color) !important;
      background: var(--bg-input) !important;
      color: var(--text-muted) !important;
      cursor: not-allowed;
      box-shadow: none !important;
    }
  }
}

/* 局部重写 Element Plus 组件以适配深色/浅色卡片 */
:deep(.el-textarea__inner) {
  background-color: var(--bg-input) !important;
  border: 1px solid var(--border-input) !important;
  color: var(--text-primary) !important;
  border-radius: 8px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05) !important;

  &::placeholder {
    color: var(--text-muted) !important;
  }

  &:hover {
    border-color: var(--border-input-hover) !important;
  }

  &:focus {
    border-color: var(--primary-color) !important;
    background-color: var(--bg-card) !important;
    box-shadow: 0 0 0 3px var(--focus-ring), inset 0 1px 2px rgba(0, 0, 0, 0.05) !important;
  }
}

:deep(.el-input__wrapper) {
  background-color: var(--bg-input) !important;
  box-shadow: 0 0 0 1px var(--border-input) inset !important;
  border-radius: 8px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:hover {
    box-shadow: 0 0 0 1px var(--border-input-hover) inset !important;
  }
  
  &.is-focus {
    background-color: var(--bg-card) !important;
    box-shadow: 0 0 0 1px var(--primary-color) inset, 0 0 0 3px var(--focus-ring) !important;
  }
}

:deep(.el-input__inner) {
  color: var(--text-primary) !important;
  &::placeholder {
    color: var(--text-muted) !important;
  }
}

:deep(.el-select__wrapper) {
  background-color: var(--bg-input) !important;
  box-shadow: 0 0 0 1px var(--border-input) inset !important;
  border-radius: 8px;
  color: var(--text-primary) !important;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:hover {
    box-shadow: 0 0 0 1px var(--border-input-hover) inset !important;
  }

  &.is-focus {
    background-color: var(--bg-card) !important;
    box-shadow: 0 0 0 1px var(--primary-color) inset, 0 0 0 3px var(--focus-ring) !important;
  }
}

:deep(.el-select-dropdown__item) {
  color: var(--text-regular);
  &.is-hovering {
    background-color: var(--border-color);
  }
  &.is-selected {
    color: var(--primary-color);
    font-weight: 600;
  }
}

:deep(.el-input-number) {
  .el-input-number__decrease,
  .el-input-number__increase {
    background: var(--bg-input);
    border-color: var(--border-input);
    color: var(--text-regular);
    transition: all 0.2s ease;

    &:hover {
      color: var(--primary-color);
      background: var(--border-color);
    }
  }

  .el-input__wrapper {
    box-shadow: 0 0 0 1px var(--border-input) inset !important;
  }
}

/* ================================================================
   颜色图谱面板样式
   ================================================================ */
.color-map {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.color-chip {
  width: 64px;
  height: 72px;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  padding-bottom: 6px;
  border: 2px solid transparent;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);

  .color-rank {
    font-size: 10px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.9);
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
    line-height: 1;
    margin-bottom: 2px;
  }

  .color-name {
    font-size: 11px;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.95);
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
    line-height: 1;
  }

  /* 浅色色块用深色文字 */
  &:has(.color-rank) {
    &:nth-child(3n+1) .color-rank,
    &:nth-child(3n+2) .color-rank {
      color: rgba(255, 255, 255, 0.9);
    }
  }

  &:hover {
    transform: translateY(-2px) scale(1.05);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.25);
    z-index: 1;
  }

  &.active {
    border-color: #ffffff;
    box-shadow: 0 0 0 3px var(--primary-color), 0 6px 16px rgba(0, 0, 0, 0.3);
    transform: translateY(-2px) scale(1.08);

    &::after {
      content: '';
      position: absolute;
      top: 4px;
      right: 4px;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #ffffff;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
    }
  }
}

.color-map-empty {
  color: var(--text-muted);
  font-size: 13px;
  padding: 10px 0;
  display: flex;
  align-items: center;
  gap: 8px;

  &::before {
    content: '';
    width: 16px;
    height: 16px;
    border: 2px solid var(--text-muted);
    border-top-color: transparent;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    display: inline-block;
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.color-map-tip {
  margin-top: 10px;
  padding: 8px 12px;
  background: rgba(59, 130, 246, 0.08);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  font-size: 12px;
  color: var(--primary-color);
  display: flex;
  align-items: flex-start;
  gap: 6px;
  line-height: 1.5;

  .el-icon {
    flex-shrink: 0;
    margin-top: 1px;
  }
}
</style>