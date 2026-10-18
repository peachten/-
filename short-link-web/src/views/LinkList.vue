<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  createLink,
  deleteLinks,
  enableLink,
  pageLinks,
  updateLink
} from '../api/link'
import { listGroups } from '../api/group'
import { copyText } from '../utils/clipboard'

const loading = ref(false)
const groups = ref([])
const list = ref([])
const total = ref(0)

const query = reactive({
  gid: '',
  keyword: '',
  orderTag: '',
  current: 1,
  size: 10
})

/* ---------- 新建 ---------- */
const createVisible = ref(false)
const createLoading = ref(false)
const createRef = ref()
const createdUrl = ref('')
const createForm = reactive({
  originUrl: '',
  gid: '',
  validDateType: 0,
  validDate: '',
  customShortUri: '',
  describe: ''
})
const createRules = {
  originUrl: [
    { required: true, message: '请输入原始链接', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: '原始链接必须以 http:// 或 https:// 开头',
      trigger: 'blur'
    }
  ],
  gid: [{ required: true, message: '请选择所属分组', trigger: 'change' }],
  customShortUri: [
    {
      pattern: /^[0-9a-zA-Z]{0,6}$/,
      message: '自定义后缀只能是 6 位以内的字母或数字',
      trigger: 'blur'
    }
  ],
  validDate: [
    {
      validator: (rule, value, callback) => {
        if (createForm.validDateType === 1 && !value) {
          callback(new Error('请选择失效时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

/* ---------- 编辑 ---------- */
const editVisible = ref(false)
const editLoading = ref(false)
const editRef = ref()
const editForm = reactive({
  fullShortUrl: '',
  gid: '',
  describe: '',
  validDateType: 0,
  validDate: ''
})
const editRules = {
  validDate: [
    {
      validator: (rule, value, callback) => {
        if (editForm.validDateType === 1 && !value) {
          callback(new Error('请选择失效时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

const formatDate = (value) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-')

// 后端 LocalDateTime 的 @JsonFormat 是 yyyy-MM-dd HH:mm:ss，统一转换后再提交
const toBackendDate = (value) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : null)

const loadGroups = async () => {
  try {
    groups.value = await listGroups()
    if (!createForm.gid && groups.value.length) {
      createForm.gid = groups.value[0].gid
    }
  } catch {
    // 错误提示已由 request 拦截器统一处理
  }
}

const loadList = async () => {
  loading.value = true
  try {
    const params = { current: query.current, size: query.size }
    if (query.gid) params.gid = query.gid
    if (query.keyword) params.keyword = query.keyword
    if (query.orderTag) params.orderTag = query.orderTag
    const page = await pageLinks(params)
    list.value = page.records
    total.value = page.total
  } catch {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.current = 1
  loadList()
}

const resetQuery = () => {
  query.gid = ''
  query.keyword = ''
  query.orderTag = ''
  query.current = 1
  loadList()
}

const handleSizeChange = () => {
  query.current = 1
  loadList()
}

const handleCopy = async (text) => {
  const ok = await copyText(text)
  if (ok) {
    ElMessage.success('已复制到剪贴板')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

const openCreate = () => {
  createdUrl.value = ''
  createForm.originUrl = ''
  createForm.gid = groups.value.length ? groups.value[0].gid : ''
  createForm.validDateType = 0
  createForm.validDate = ''
  createForm.customShortUri = ''
  createForm.describe = ''
  createVisible.value = true
  nextTick(() => createRef.value?.clearValidate())
}

const submitCreate = async () => {
  try {
    await createRef.value.validate()
  } catch {
    return
  }
  createLoading.value = true
  try {
    const data = await createLink({
      originUrl: createForm.originUrl,
      gid: createForm.gid,
      validDateType: createForm.validDateType,
      validDate: createForm.validDateType === 1 ? toBackendDate(createForm.validDate) : null,
      customShortUri: createForm.customShortUri || undefined,
      describe: createForm.describe || undefined
    })
    createdUrl.value = data.fullShortUrl
    ElMessage.success(data.idempotent ? '该链接已存在，已返回原短链' : '创建成功')
    await loadList()
  } catch {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    createLoading.value = false
  }
}

const openEdit = (row) => {
  editForm.fullShortUrl = row.fullShortUrl
  editForm.gid = row.gid
  editForm.describe = row.description || ''
  editForm.validDateType = row.validDateType ?? 0
  // 后端返回的是 ISO 格式（2026-12-31T23:59:59），转成日期组件与接口需要的格式
  editForm.validDate = row.validDate ? toBackendDate(row.validDate) : ''
  editVisible.value = true
  nextTick(() => editRef.value?.clearValidate())
}

const submitEdit = async () => {
  try {
    await editRef.value.validate()
  } catch {
    return
  }
  editLoading.value = true
  try {
    await updateLink({
      fullShortUrl: editForm.fullShortUrl,
      gid: editForm.gid,
      describe: editForm.describe,
      validDateType: editForm.validDateType,
      validDate: editForm.validDateType === 1 ? toBackendDate(editForm.validDate) : null
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    await loadList()
  } catch {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    editLoading.value = false
  }
}

const handleEnable = async (row, enabled) => {
  try {
    await enableLink(row.fullShortUrl, enabled ? 0 : 1)
    row.enableStatus = enabled ? 0 : 1
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch {
    // 失败时保持原状态（开关绑定的是 row.enableStatus）
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除短链 ${row.fullShortUrl} 吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteLinks([row.fullShortUrl])
    ElMessage.success('删除成功')
    if (list.value.length === 1 && query.current > 1) {
      query.current -= 1
    }
    await loadList()
  } catch {
    // 错误提示已由 request 拦截器统一处理
  }
}

onMounted(async () => {
  await loadGroups()
  await loadList()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>短链管理</span>
        <el-button type="primary" @click="openCreate">新建短链</el-button>
      </div>
    </template>

    <!-- 筛选区 -->
    <el-form :inline="true" class="filter">
      <el-form-item label="分组">
        <el-select v-model="query.gid" placeholder="全部分组" clearable style="width: 180px" @change="handleSearch">
          <el-option v-for="g in groups" :key="g.gid" :label="g.name" :value="g.gid" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="备注 / 原始链接"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="排序">
        <el-select v-model="query.orderTag" placeholder="默认" clearable style="width: 140px" @change="handleSearch">
          <el-option label="创建时间" value="createTime" />
          <el-option label="点击次数" value="clickNum" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe empty-text="暂无短链，点击右上角新建">
      <el-table-column label="短链" min-width="230">
        <template #default="{ row }">
          <div class="short-url">
            <span>{{ row.fullShortUrl }}</span>
            <el-button link type="primary" @click="handleCopy(row.fullShortUrl)">复制</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="originUrl" label="原始链接" min-width="220" show-overflow-tooltip />
      <el-table-column prop="clickNum" label="点击次数" width="100" align="center" />
      <el-table-column label="有效期" width="160">
        <template #default="{ row }">
          {{ row.validDateType === 1 ? formatDate(row.validDate) : '永久' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enableStatus === 0"
            @change="(val) => handleEnable(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="description" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="loadList"
      />
    </div>

    <!-- 新建弹窗 -->
    <el-dialog v-model="createVisible" title="新建短链" width="560px" @closed="createdUrl = ''">
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="原始链接" prop="originUrl">
          <el-input v-model="createForm.originUrl" placeholder="https://example.com/xxx" />
        </el-form-item>
        <el-form-item label="所属分组" prop="gid">
          <el-select v-model="createForm.gid" placeholder="请选择分组" style="width: 100%">
            <el-option v-for="g in groups" :key="g.gid" :label="g.name" :value="g.gid" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期">
          <el-radio-group v-model="createForm.validDateType">
            <el-radio :value="0">永久</el-radio>
            <el-radio :value="1">自定义</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.validDateType === 1" label="失效时间" prop="validDate">
          <el-date-picker
            v-model="createForm.validDate"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择失效时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="自定义后缀" prop="customShortUri">
          <el-input
            v-model="createForm.customShortUri"
            maxlength="6"
            placeholder="选填，6 位以内字母或数字"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.describe" maxlength="255" placeholder="选填" />
        </el-form-item>
      </el-form>

      <el-alert v-if="createdUrl" type="success" :closable="false" title="短链已生成">
        <div class="result-line">
          <span>{{ createdUrl }}</span>
          <el-button link type="primary" @click="handleCopy(createdUrl)">复制</el-button>
        </div>
      </el-alert>

      <template #footer>
        <el-button @click="createVisible = false">关闭</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑短链" width="520px">
      <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="短链">
          <span class="edit-url">{{ editForm.fullShortUrl }}</span>
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="editForm.gid" style="width: 100%">
            <el-option v-for="g in groups" :key="g.gid" :label="g.name" :value="g.gid" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期">
          <el-radio-group v-model="editForm.validDateType">
            <el-radio :value="0">永久</el-radio>
            <el-radio :value="1">自定义</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.validDateType === 1" label="失效时间" prop="validDate">
          <el-date-picker
            v-model="editForm.validDate"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择失效时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.describe" maxlength="255" placeholder="选填" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.filter {
  margin-bottom: 4px;
}

.short-url {
  display: flex;
  align-items: center;
  gap: 8px;
  word-break: break-all;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.result-line {
  display: flex;
  align-items: center;
  gap: 12px;
  word-break: break-all;
}

.edit-url {
  color: #374151;
  word-break: break-all;
}
</style>
