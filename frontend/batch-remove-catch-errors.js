#!/usr/bin/env node

/**
 * 批量移除 Vue 文件中 catch 块里的 message.error 调用
 * 保留参数验证等其他地方的 message.error
 */

const fs = require('fs');
const path = require('path');

const files = [
  'frontend/src/views/ApplicationManagement.vue',
  'frontend/src/views/MiddlewareManagement.vue',
  'frontend/src/views/NodeManagement.vue',
  'frontend/src/views/OperationLog.vue'
];

function processFile(filePath) {
  console.log(`\n📝 处理文件: ${filePath}`);
  
  if (!fs.existsSync(filePath)) {
    console.log(`  ⚠️  文件不存在`);
    return;
  }
  
  // 读取文件内容
  let content = fs.readFileSync(filePath, 'utf8');
  const originalContent = content;
  
  // 备份文件
  fs.writeFileSync(`${filePath}.bak`, content);
  console.log(`  ✅ 已备份`);
  
  let modifiedCount = 0;
  
  // 模式1: catch 块中的 message.error (带 error.message)
  const pattern1 = /(\} catch \(error: any\) \{[\s\S]*?console\.error\([^)]+\)[;\s]*)\s*message\.error\(error\.message \|\| [^)]+\)/g;
  content = content.replace(pattern1, (match, prefix) => {
    modifiedCount++;
    return prefix + '\n    // 错误消息已在 request.ts 中显示，这里不再重复';
  });
  
  // 模式2: catch 块中的简单 message.error
  const pattern2 = /(\} catch \(error: any\) \{[\s\S]*?console\.error\([^)]+\)[;\s]*)\s*message\.error\([^)]+\)/g;
  content = content.replace(pattern2, (match, prefix) => {
    // 确保不是已经被模式1处理过的
    if (!match.includes('// 错误消息已在 request.ts 中显示')) {
      modifiedCount++;
      return prefix + '\n    // 错误消息已在 request.ts 中显示，这里不再重复';
    }
    return match;
  });
  
  if (content !== originalContent) {
    fs.writeFileSync(filePath, content);
    console.log(`  ✅ 已修改 ${modifiedCount} 处`);
  } else {
    console.log(`  ℹ️  无需修改`);
  }
}

console.log('🚀 开始批量处理...\n');

files.forEach(file => {
  try {
    processFile(file);
  } catch (error) {
    console.error(`  ❌ 处理失败:`, error.message);
  }
});

console.log('\n✅ 处理完成！');
console.log('\n📋 下一步：');
console.log('1. 检查修改的文件');
console.log('2. 测试功能是否正常');
console.log('3. 如果满意，删除 .bak 备份文件: rm frontend/src/views/*.bak');
console.log('4. 如果不满意，恢复备份: for f in frontend/src/views/*.bak; do mv "$f" "${f%.bak}"; done');
