<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { deleteGroup, listGroups, saveGroup, updateGroup } from '../api/group'

const loading = ref(false)
const groups = ref([])

const createVisible = ref(false)
const createLoading = ref(false)
const createRef = ref()
const createForm = reactive({ name: '' })
const createRules = {
  name: [
    { required: true, message: '请输入分组名称', trigger: 'blur' },
    { max: 64, message: '分组名称长度不能超过 64', trigger: 'blur' }
  ]
}

const renameVisible = ref(false)
const renameLoading = ref(false)
const renameRef = ref()
const renameForm = reactive({ gid: '', name: '' })
const renameRules = {
  name: [
    { required: true, message: '请输入分组名称', trigger: 'blur' },
    { max: 64, message: '分组名称长度不能超过 64', trigger: 'blur' }
  ]
}

const formatDate = (value) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-')

const loadList = async () => {
  loading.value = true
  try {
    groups.value = await listGroups()
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  createForm.name = ''
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
    await saveGroup(createForm.name)
    ElMessage.success('创建成功')
    createVisible.value = false
    await loadList()
  } finally {
    createLoading.value = false
  }
}

const openRename = (row) => {
  renameForm.gid = row.gid
  renameForm.name = row.name
  renameVisible.value = true
  nextTick(() => renameRef.value?.clearValidate())
}

const submitRename = async () => {
  try {
    await renameRef.value.validate()
  } catch {
    return
  }
  renameLoading.value = true
  try {
    await updateGroup({ gid: renameForm.gid, name: renameForm.name })
    ElMessage.success('修改成功')
    renameVisible.value = false
    await loadList()
  } finally {
    renameLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定删除分组「${row.name}」吗？分组下存在短链时无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await deleteGroup(row.gid)
  ElMessage.success('删除成功')
  await loadList()
}

onMounted(loadList)
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>分组管理</span>
        <el-button type="primary" @click="openCreate">新建分组</el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="groups" border stripe>
      <el-table-column prop="name" label="分组名称" min-width="180" />
      <el-table-column prop="gid" label="分组标识" min-width="140" />
      <el-table-column prop="linkCount" label="短链数量" width="110" align="center" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRename(row)">重命名</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建分组" width="420px">
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="分组名称" prop="name">
          <el-input v-model="createForm.name" maxlength="64" placeholder="请输入分组名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameVisible" title="重命名分组" width="420px">
      <el-form ref="renameRef" :model="renameForm" :rules="renameRules" label-width="90px">
        <el-form-item label="分组名称" prop="name">
          <el-input v-model="renameForm.name" maxlength="64" placeholder="请输入分组名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="renameLoading" @click="submitRename">保存</el-button>
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
</style>
