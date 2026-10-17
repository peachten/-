<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { listGroups } from '../api/group'
import { pageLinks } from '../api/link'
import { getDimensionStats, getGroupStats, pageAccessRecords } from '../api/stats'

// 按需引入，避免把整个 ECharts 打进包里
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer
])

/* ---------- 筛选条件 ---------- */
const groups = ref([])
const links = ref([])
const gid = ref('all')
const fullShortUrl = ref('')
const rangeType = ref('7')
const customRange = ref([])

/* ---------- 数据 ---------- */
const loading = ref(false)
const stats = reactive({ pv: 0, uv: 0, uip: 0, hourStats: [], daily: [] })
const browserStats = ref([])
const osStats = ref([])
const records = ref([])
const linkTotal = ref(0)

const dateRange = computed(() => {
  const today = dayjs()
  if (rangeType.value === '30') {
    return [today.subtract(29, 'day').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')]
  }
  if (rangeType.value === 'custom' && customRange.value?.length === 2) {
    return customRange.value
  }
  return [today.subtract(6, 'day').format('YYYY-MM-DD'), today.format('YYYY-MM-DD')]
})

const hasDaily = computed(() => stats.daily?.some((item) => item.pv > 0))
const hasHour = computed(() => stats.hourStats?.some((value) => value > 0))

/* ---------- 图表 ---------- */
const lineRef = ref(null)
const hourRef = ref(null)
const browserRef = ref(null)
const osRef = ref(null)
let lineChart = null
let hourChart = null
let browserChart = null
let osChart = null

const ensureChart = (chart, el) => {
  if (chart && chart.getDom() !== el) {
    chart.dispose()
    chart = null
  }
  return chart || echarts.init(el)
}

const renderLine = () => {
  if (!lineRef.value) {
    lineChart?.dispose()
    lineChart = null
    return
  }
  lineChart = ensureChart(lineChart, lineRef.value)
  lineChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      legend: { data: ['PV', 'UV'], right: 10 },
      grid: { left: 44, right: 20, top: 44, bottom: 30 },
      xAxis: {
        type: 'category',
        data: stats.daily.map((item) => item.date.slice(5)),
        boundaryGap: false
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: 'PV',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: stats.daily.map((item) => item.pv),
          areaStyle: { opacity: 0.12 }
        },
        {
          name: 'UV',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: stats.daily.map((item) => item.uv)
        }
      ]
    },
    true
  )
  lineChart.resize()
}

const renderHour = () => {
  if (!hourRef.value) {
    hourChart?.dispose()
    hourChart = null
    return
  }
  hourChart = ensureChart(hourChart, hourRef.value)
  hourChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      grid: { left: 44, right: 16, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: Array.from({ length: 24 }, (_, hour) => `${hour}`),
        axisLabel: { interval: 1 }
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          name: '访问量',
          type: 'bar',
          barMaxWidth: 16,
          itemStyle: { color: '#409eff', borderRadius: [3, 3, 0, 0] },
          data: stats.hourStats
        }
      ]
    },
    true
  )
  hourChart.resize()
}

const renderPie = (chart, el, data, colorList) => {
  if (!el) {
    chart?.dispose()
    return null
  }
  const instance = ensureChart(chart, el)
  instance.setOption(
    {
      color: colorList,
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, icon: 'circle' },
      series: [
        {
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b} {d}%' },
          data
        }
      ]
    },
    true
  )
  instance.resize()
  return instance
}

const renderAll = () => {
  renderLine()
  renderHour()
  browserChart = renderPie(
    browserChart,
    browserRef.value,
    browserStats.value,
    ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
  )
  osChart = renderPie(
    osChart,
    osRef.value,
    osStats.value,
    ['#67c23a', '#409eff', '#e6a23c', '#f56c6c', '#909399']
  )
}

const handleResize = () => {
  ;[lineChart, hourChart, browserChart, osChart].forEach((chart) => chart && chart.resize())
}

/* ---------- 数据加载 ---------- */
const loadGroups = async () => {
  groups.value = await listGroups()
}

const loadLinks = async () => {
  const params = { current: 1, size: 200 }
  if (gid.value !== 'all') {
    params.gid = gid.value
  }
  const page = await pageLinks(params)
  links.value = page.records
  linkTotal.value = page.total
}

const loadAll = async () => {
  const [startDate, endDate] = dateRange.value
  const params = { gid: gid.value, startDate, endDate }
  if (fullShortUrl.value) {
    params.fullShortUrl = fullShortUrl.value
  }

  loading.value = true
  try {
    const [summary, browser, os, accessPage] = await Promise.all([
      getGroupStats(params),
      getDimensionStats({ ...params, dimension: 'browser' }),
      getDimensionStats({ ...params, dimension: 'os' }),
      pageAccessRecords({ ...params, current: 1, size: 10 })
    ])

    stats.pv = summary.pv
    stats.uv = summary.uv
    stats.uip = summary.uip
    stats.hourStats = summary.hourStats
    stats.daily = summary.daily
    browserStats.value = browser
    osStats.value = os
    records.value = accessPage.records

    await nextTick()
    renderAll()
  } finally {
    loading.value = false
  }
}

const handleGroupChange = async () => {
  fullShortUrl.value = ''
  await loadLinks()
  await loadAll()
}

const handleRangeChange = () => {
  if (rangeType.value === 'custom' && customRange.value?.length !== 2) {
    return
  }
  loadAll()
}

const handleCustomRange = () => {
  if (customRange.value?.length === 2) {
    loadAll()
  }
}

const formatTime = (value) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-')

onMounted(async () => {
  await loadGroups()
  await loadLinks()
  await loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  ;[lineChart, hourChart, browserChart, osChart].forEach((chart) => chart?.dispose())
})
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <!-- 指标卡片 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-label">PV（访问量）</div>
          <div class="stat-value">{{ stats.pv }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-label">UV（独立访客）</div>
          <div class="stat-value">{{ stats.uv }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-label">UIP（独立 IP）</div>
          <div class="stat-value">{{ stats.uip }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-label">短链总数</div>
          <div class="stat-value">{{ linkTotal }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="分组">
          <el-select v-model="gid" style="width: 180px" @change="handleGroupChange">
            <el-option label="全部分组" value="all" />
            <el-option v-for="g in groups" :key="g.gid" :label="g.name" :value="g.gid" />
          </el-select>
        </el-form-item>
        <el-form-item label="短链">
          <el-select
            v-model="fullShortUrl"
            clearable
            placeholder="全部短链"
            style="width: 260px"
            @change="loadAll"
          >
            <el-option
              v-for="link in links"
              :key="link.fullShortUrl"
              :label="`${link.shortUri} · ${link.description || link.originUrl}`"
              :value="link.fullShortUrl"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-radio-group v-model="rangeType" @change="handleRangeChange">
            <el-radio-button value="7">近 7 天</el-radio-button>
            <el-radio-button value="30">近 30 天</el-radio-button>
            <el-radio-button value="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="rangeType === 'custom'">
          <el-date-picker
            v-model="customRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            @change="handleCustomRange"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 趋势 + 小时分布 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card class="chart-card">
          <template #header>PV / UV 趋势</template>
          <div v-if="hasDaily" ref="lineRef" class="chart"></div>
          <el-empty v-else description="暂无访问数据" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card class="chart-card">
          <template #header>24 小时访问分布</template>
          <div v-if="hasHour" ref="hourRef" class="chart"></div>
          <el-empty v-else description="暂无访问数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 两个饼图 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>浏览器占比</template>
          <div v-if="browserStats.length" ref="browserRef" class="chart pie"></div>
          <el-empty v-else description="暂无浏览器数据" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>操作系统占比</template>
          <div v-if="osStats.length" ref="osRef" class="chart pie"></div>
          <el-empty v-else description="暂无操作系统数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近访问明细 -->
    <el-card>
      <template #header>最近访问明细</template>
      <el-table :data="records" border stripe empty-text="暂无访问明细">
        <el-table-column label="访问时间" width="180">
          <template #default="{ row }">{{ formatTime(row.accessTime) }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="browser" label="浏览器" min-width="120" />
        <el-table-column prop="os" label="操作系统" min-width="180" show-overflow-tooltip />
        <el-table-column prop="deviceType" label="设备" width="110" />
        <el-table-column prop="locale" label="语言" width="110" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-card {
  margin-bottom: 0;
}

.stat-label {
  color: #6b7280;
  font-size: 13px;
}

.stat-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  color: #111827;
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.chart-card {
  margin-bottom: 0;
}

.chart {
  height: 320px;
}

.chart.pie {
  height: 300px;
}

:deep(.el-empty) {
  padding: 40px 0;
}
</style>
