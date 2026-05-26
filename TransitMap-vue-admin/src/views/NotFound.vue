<template>
  <div class="not-found">
    <div class="nf-content">
      <h1>404</h1>
      <p>页面不存在</p>
      <el-button type="primary" @click="$router.push('/')">返回控制台</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f5f7fa;
}
.nf-content { text-align: center; }
.nf-content h1 {
  font-size: 96px;
  color: #409eff;
  margin: 0;
  line-height: 1;
}
.nf-content p {
  font-size: 18px;
  color: #909399;
  margin: 16px 0 24px;
}
</style>
