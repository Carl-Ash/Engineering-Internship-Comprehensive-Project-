/**
 * 将数据导出为 CSV 文件（纯前端，无需后端接口）
 * @param columns 列配置 [{ title: '列名', dataIndex: '字段名' }]
 * @param data 数据数组
 * @param filename 导出文件名
 */
export function exportCsv(
  columns: { title: string; dataIndex?: string; key?: string }[],
  data: Record<string, any>[],
  filename = 'export.csv',
) {
  // 只保留有 dataIndex 的列
  const validCols = columns.filter((c) => c.dataIndex)

  // CSV 头
  const header = validCols.map((c) => `"${c.title}"`).join(',')

  // CSV 行
  const rows = data.map((row) =>
    validCols
      .map((c) => {
        const val = row[c.dataIndex!]
        if (val === null || val === undefined) return ''
        const str = String(val)
        // 引号转义
        return `"${str.replace(/"/g, '""')}"`
      })
      .join(','),
  )

  const BOM = '﻿'
  const csv = BOM + header + '\n' + rows.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)

  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
