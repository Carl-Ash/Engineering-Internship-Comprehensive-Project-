/**
 * 可视化编辑器工具类
 * 负责管理 iframe 内的可视化编辑功能，包括脚本注入、事件监听、元素选择等
 */

export interface ElementInfo {
  tagName: string
  id: string
  className: string
  textContent: string
  selector: string
  pagePath: string
  rect: {
    top: number
    left: number
    width: number
    height: number
  }
}

export interface VisualEditorOptions {
  onElementSelected?: (elementInfo: ElementInfo) => void
  onElementHover?: (elementInfo: ElementInfo) => void
}

export class VisualEditor {
  private iframe: HTMLIFrameElement | null = null
  private isEditMode = false
  private options: VisualEditorOptions

  constructor(options: VisualEditorOptions = {}) {
    this.options = options
  }

  /** 初始化编辑器，绑定 iframe */
  init(iframe: HTMLIFrameElement) {
    this.iframe = iframe
  }

  /** 进入编辑模式 */
  enableEditMode() {
    if (!this.iframe) return
    this.isEditMode = true
    setTimeout(() => {
      this.injectEditScript()
    }, 300)
  }

  /** 退出编辑模式 */
  disableEditMode() {
    this.isEditMode = false
    this.sendMessageToIframe({ type: 'TOGGLE_EDIT_MODE', editMode: false })
    this.sendMessageToIframe({ type: 'CLEAR_ALL_EFFECTS' })
  }

  /** 切换编辑模式，返回当前状态 */
  toggleEditMode() {
    if (this.isEditMode) {
      this.disableEditMode()
    } else {
      this.enableEditMode()
    }
    return this.isEditMode
  }

  /** 同步状态，确保非编辑模式时清理残留效果 */
  syncState() {
    if (!this.isEditMode) {
      this.sendMessageToIframe({ type: 'CLEAR_ALL_EFFECTS' })
    }
  }

  /** 清除 iframe 内的选中元素 */
  clearSelection() {
    this.sendMessageToIframe({ type: 'CLEAR_SELECTION' })
  }

  /** iframe 加载完成时调用，根据编辑模式注入或清理 */
  onIframeLoad() {
    if (this.isEditMode) {
      setTimeout(() => {
        this.injectEditScript()
      }, 500)
    } else {
      setTimeout(() => {
        this.syncState()
      }, 500)
    }
  }

  /** 处理来自 iframe 的 postMessage */
  handleIframeMessage(event: MessageEvent) {
    const { type, data } = event.data
    switch (type) {
      case 'ELEMENT_SELECTED':
        if (this.options.onElementSelected && data?.elementInfo) {
          this.options.onElementSelected(data.elementInfo)
        }
        break
      case 'ELEMENT_HOVER':
        if (this.options.onElementHover && data?.elementInfo) {
          this.options.onElementHover(data.elementInfo)
        }
        break
    }
  }

  /** 向 iframe 发送消息 */
  private sendMessageToIframe(message: Record<string, any>) {
    if (this.iframe?.contentWindow) {
      this.iframe.contentWindow.postMessage(message, '*')
    }
  }

  /** 注入编辑脚本到 iframe */
  private injectEditScript() {
    if (!this.iframe) return

    const waitForIframeLoad = () => {
      try {
        if (this.iframe!.contentWindow && this.iframe!.contentDocument) {
          if (this.iframe!.contentDocument.getElementById('visual-edit-script')) {
            this.sendMessageToIframe({ type: 'TOGGLE_EDIT_MODE', editMode: true })
            return
          }
          const script = this.generateEditScript()
          const scriptElement = this.iframe!.contentDocument.createElement('script')
          scriptElement.id = 'visual-edit-script'
          scriptElement.textContent = script
          this.iframe!.contentDocument.head.appendChild(scriptElement)
        } else {
          setTimeout(waitForIframeLoad, 100)
        }
      } catch {
        // 跨域时静默处理
      }
    }

    waitForIframeLoad()
  }

  /** 生成要注入的编辑脚本 */
  private generateEditScript() {
    return `
(function() {
  let isEditMode = true
  let currentHoverElement = null
  let currentSelectedElement = null

  // 注入悬浮/选中样式
  function injectStyles() {
    if (document.getElementById('edit-mode-styles')) return
    const style = document.createElement('style')
    style.id = 'edit-mode-styles'
    style.textContent = \`
      .edit-hover {
        outline: 2px dashed #1890ff !important;
        outline-offset: 2px !important;
        cursor: crosshair !important;
        transition: outline 0.2s ease !important;
        position: relative !important;
      }
      .edit-hover::before {
        content: '' !important;
        position: absolute !important;
        top: -4px !important;
        left: -4px !important;
        right: -4px !important;
        bottom: -4px !important;
        background: rgba(24, 144, 255, 0.02) !important;
        pointer-events: none !important;
        z-index: -1 !important;
      }
      .edit-selected {
        outline: 3px solid #52c41a !important;
        outline-offset: 2px !important;
        cursor: default !important;
        position: relative !important;
      }
      .edit-selected::before {
        content: '' !important;
        position: absolute !important;
        top: -4px !important;
        left: -4px !important;
        right: -4px !important;
        bottom: -4px !important;
        background: rgba(82, 196, 26, 0.03) !important;
        pointer-events: none !important;
        z-index: -1 !important;
      }
    \`
    document.head.appendChild(style)
  }

  // 生成 CSS 选择器路径
  function generateSelector(element) {
    const path = []
    let current = element
    while (current && current !== document.body) {
      let selector = current.tagName.toLowerCase()
      if (current.id) {
        selector += '#' + current.id
        path.unshift(selector)
        break
      }
      if (current.className) {
        const classes = current.className.split(' ').filter(function(c) { return c && !c.startsWith('edit-') })
        if (classes.length > 0) selector += '.' + classes.join('.')
      }
      const siblings = Array.from(current.parentElement ? current.parentElement.children : [])
      const index = siblings.indexOf(current) + 1
      selector += ':nth-child(' + index + ')'
      path.unshift(selector)
      current = current.parentElement
    }
    return path.join(' > ')
  }

  // 收集元素信息
  function getElementInfo(element) {
    const rect = element.getBoundingClientRect()
    let pagePath = window.location.search + window.location.hash
    if (!pagePath) pagePath = ''
    return {
      tagName: element.tagName,
      id: element.id,
      className: element.className,
      textContent: (element.textContent || '').trim().substring(0, 100),
      selector: generateSelector(element),
      pagePath: pagePath,
      rect: { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
    }
  }

  // 清除悬浮效果
  function clearHoverEffect() {
    if (currentHoverElement) {
      currentHoverElement.classList.remove('edit-hover')
      currentHoverElement = null
    }
  }

  // 清除选中效果
  function clearSelectedEffect() {
    document.querySelectorAll('.edit-selected').forEach(function(el) { el.classList.remove('edit-selected') })
    currentSelectedElement = null
  }

  let eventListenersAdded = false

  function addEventListeners() {
    if (eventListenersAdded) return

    document.body.addEventListener('mouseover', function(event) {
      if (!isEditMode) return
      const target = event.target
      if (target === currentHoverElement || target === currentSelectedElement) return
      if (target === document.body || target === document.documentElement) return
      if (target.tagName === 'SCRIPT' || target.tagName === 'STYLE') return
      clearHoverEffect()
      target.classList.add('edit-hover')
      currentHoverElement = target
    }, true)

    document.body.addEventListener('mouseout', function(event) {
      if (!isEditMode) return
      const target = event.target
      if (!event.relatedTarget || !target.contains(event.relatedTarget)) {
        clearHoverEffect()
      }
    }, true)

    document.body.addEventListener('click', function(event) {
      if (!isEditMode) return
      event.preventDefault()
      event.stopPropagation()
      const target = event.target
      if (target === document.body || target === document.documentElement) return
      if (target.tagName === 'SCRIPT' || target.tagName === 'STYLE') return
      clearSelectedEffect()
      clearHoverEffect()
      target.classList.add('edit-selected')
      currentSelectedElement = target
      const elementInfo = getElementInfo(target)
      try {
        window.parent.postMessage({ type: 'ELEMENT_SELECTED', data: { elementInfo: elementInfo } }, '*')
      } catch(e) {}
    }, true)

    eventListenersAdded = true
  }

  // 监听父窗口消息
  window.addEventListener('message', function(event) {
    const msg = event.data
    if (!msg) return
    switch (msg.type) {
      case 'TOGGLE_EDIT_MODE':
        isEditMode = msg.editMode
        if (isEditMode) {
          injectStyles()
          addEventListeners()
          showEditTip()
        } else {
          clearHoverEffect()
          clearSelectedEffect()
        }
        break
      case 'CLEAR_SELECTION':
        clearSelectedEffect()
        break
      case 'CLEAR_ALL_EFFECTS':
        isEditMode = false
        clearHoverEffect()
        clearSelectedEffect()
        var tip = document.getElementById('edit-tip')
        if (tip) tip.remove()
        break
    }
  })

  // 显示编辑提示
  function showEditTip() {
    if (document.getElementById('edit-tip')) return
    var tip = document.createElement('div')
    tip.id = 'edit-tip'
    tip.innerHTML = '\\uD83C\\uDFAF 编辑模式已开启<br/>悬浮查看元素，点击选中元素'
    tip.style.cssText = 'position:fixed;top:20px;right:20px;background:#1890ff;color:#fff;padding:12px 16px;border-radius:6px;font-size:14px;z-index:9999;box-shadow:0 4px 12px rgba(0,0,0,0.15);animation:editTipIn 0.3s ease;'
    var animStyle = document.createElement('style')
    animStyle.textContent = '@keyframes editTipIn{from{opacity:0;transform:translateY(-10px)}to{opacity:1;transform:translateY(0)}}'
    document.head.appendChild(animStyle)
    document.body.appendChild(tip)
    setTimeout(function() {
      if (tip.parentNode) {
        tip.style.animation = 'editTipIn 0.3s ease reverse'
        setTimeout(function() { tip.remove() }, 300)
      }
    }, 3000)
  }

  injectStyles()
  addEventListeners()
  showEditTip()
})()`
  }
}
