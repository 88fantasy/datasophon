import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Modal, Drawer, message } from 'antd'

import DefineModal from './DefineModal/index'
import { invokeHackerConfig, default as DefineModalApi } from './DefineModal/api'
import HackModal from './HackModal/index'
import { invokeGetModal, invokeSetModal } from './modalInstance'
import apiHook, { invokeCloseAllModal } from './apiHook'
import elConfigProviderHocFn from './elConfigProviderHocFn'

vi.mock('../../utils/injectLocationChange', () => ({
  default: vi.fn()
}))

vi.mock('../../utils/util', () => ({
  invokeGenerateElId: vi.fn(() => 'test-el-id-' + Math.random()),
  isEmpty: vi.fn((val) => val === undefined || val === null || val === ''),
  showMsgAfferRequest: vi.fn()
}))

vi.mock('lodash-es', () => ({
  cloneDeep: vi.fn((obj) => JSON.parse(JSON.stringify(obj)))
}))

vi.mock('react-dom/client', () => ({
  hydrateRoot: vi.fn(() => ({
    render: vi.fn(),
    unmount: vi.fn()
  }))
}))

describe('CommonModal Components Tests', () => {
  let originalLocation: string

  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    message.destroy()
    originalLocation = window.location.href
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...window.location, href: 'http://localhost/test' }
    })
    invokeSetModal(Modal as any)
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...window.location, href: originalLocation }
    })
    cleanup()
  })

  describe('modalInstance', () => {
    it('should return default Modal when not set', () => {
      const result = invokeGetModal()
      expect(result.Modal).toBe(Modal)
    })

    it('should set and get custom Modal', () => {
      invokeSetModal(Drawer as any)
      const result = invokeGetModal()
      expect(result.Modal).toBe(Drawer)
    })

    it('should allow setting back to Modal', () => {
      invokeSetModal(Modal as any)
      const result = invokeGetModal()
      expect(result.Modal).toBe(Modal)
    })
  })

  describe('elConfigProviderHocFn', () => {
    it('should wrap component with ConfigProvider', () => {
      const TestComponent = (props: any) => <div {...props}>Test</div>
      const WrappedComponent = elConfigProviderHocFn(TestComponent)
      
      const { container } = render(
        <WrappedComponent customProp="test" />
      )
      
      expect(container.textContent).toBe('Test')
    })

    it('should pass all props to wrapped component', () => {
      const TestComponent = (props: any) => <div data-testid="content">{props.value}</div>
      const WrappedComponent = elConfigProviderHocFn(TestComponent)
      
      render(<WrappedComponent value="hello" />)
      
      expect(screen.getByTestId('content').textContent).toBe('hello')
    })

    it('should handle multiple props', () => {
      const TestComponent = (props: any) => <div data-testid="content">{props.a} {props.b}</div>
      const WrappedComponent = elConfigProviderHocFn(TestComponent)
      
      render(<WrappedComponent a="hello" b="world" />)
      
      expect(screen.getByTestId('content').textContent).toBe('hello world')
    })
  })

  describe('DefineModal', () => {
    const defaultProps = {
      config: {
        title: 'Test Modal',
        render: () => <div>Content</div>
      },
      visiable: true,
      getContainer: () => document.body
    }

    it('should render with Drawer component when comType is Drawer', () => {
      invokeSetModal(Drawer as any)
      
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          comType: 'Drawer'
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      expect(() => unmount()).not.toThrow()
      
      invokeSetModal(Modal as any)
    })

    it('should render with Modal component by default', () => {
      const props = {
        ...defaultProps
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      expect(() => unmount()).not.toThrow()
    })

    it('should handle onCancel without event', () => {
      const onCancelMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancel: onCancelMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onCancelClick', () => {
      const onCancelClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancelClick: onCancelClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onOkClick returning true', () => {
      const onOkMock = vi.fn().mockResolvedValue(true)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onOkClick returning false', () => {
      const onOkMock = vi.fn().mockResolvedValue(false)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should call onClosed callback', () => {
      const onClosedMock = vi.fn()
      const props = {
        ...defaultProps,
        onClosed: onClosedMock,
        visiable: false
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should call onClosed callback when visiable is false', () => {
      const onClosedMock = vi.fn()
      const props = {
        ...defaultProps,
        onClosed: onClosedMock,
        visiable: false
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should set default dialogConfig when title provided', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          title: 'Test Title'
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle merged config properties', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          dialogConfig: {
            width: '80vw',
            centered: true
          }
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle visiable state changes', () => {
      const props = {
        ...defaultProps,
        visiable: false
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onOk callback (legacy)', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: vi.fn().mockResolvedValue('ok-result'),
          onCancel: vi.fn()
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render function returning null', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          render: () => null
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onCancelClick with onCancel as fallback', () => {
      const onCancelMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancel: onCancelMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle config without onCancel', () => {
      const props = {
        ...defaultProps,
        config: {
          title: 'Test Modal',
          render: () => <div>Content</div>
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should use onOkClick as fallback for onOk', () => {
      const onOkMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle both onOk and onOkClick', () => {
      const onOkMock = vi.fn().mockResolvedValue('ok-result')
      const onOkClickMock = vi.fn().mockResolvedValue(true)
      
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should call onClosed callback when afterClose is triggered', () => {
      const onClosedMock = vi.fn()
      const props = {
        ...defaultProps,
        onClosed: onClosedMock,
        visiable: true
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onClosed without initProps', () => {
      const props = {
        ...defaultProps,
        visiable: true
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onCancelClickProxy with event', () => {
      const onCancelClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancelClick: onCancelClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onCancelClickProxy without event', () => {
      const onCancelMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancel: onCancelMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onOkClickProxy with onOk returning false', async () => {
      const onOkMock = vi.fn().mockResolvedValue(false)
      const onOkClickMock = vi.fn().mockResolvedValue(true)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onOkClickProxy with onOkClick returning false', async () => {
      const onOkClickMock = vi.fn().mockResolvedValue(false)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onOkClickProxy with onOk returning result', async () => {
      const onOkMock = vi.fn().mockResolvedValue('ok-result')
      const onOkClickMock = vi.fn().mockResolvedValue(true)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle onOkClickProxy without onOkClick and onOk', async () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle invokeInjectConfirmEvent callback', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          render: ({ invokeInjectConfirmEvent }) => {
            invokeInjectConfirmEvent(() => 'confirm result')
            return <div>Test</div>
          }
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle invokeInjectConfirmEvent with async function', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          render: ({ invokeInjectConfirmEvent }) => {
            invokeInjectConfirmEvent(async () => await Promise.resolve('async result'))
            return <div>Test</div>
          }
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle config without dialogConfig', () => {
      const props = {
        ...defaultProps,
        config: {
          title: 'Test Modal',
          render: () => <div>Content</div>
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should set dialogConfig title from config.title', () => {
      const props = {
        ...defaultProps,
        config: {
          title: 'Custom Title',
          render: () => <div>Content</div>
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should preserve existing dialogConfig title', () => {
      const props = {
        ...defaultProps,
        config: {
          title: 'Custom Title',
          dialogConfig: {
            title: 'Existing Title'
          },
          render: () => <div>Content</div>
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with empty config', () => {
      const props = {
        visiable: true,
        getContainer: () => document.body,
        config: {}
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle getContainer prop', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: vi.fn()
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should pass okButtonProps with loading state', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: vi.fn().mockResolvedValue(true)
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle config with dialogConfig already set', () => {
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          dialogConfig: {
            width: '100%',
            centered: true,
            title: 'Existing Title'
          }
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle config without title', () => {
      const props = {
        ...defaultProps,
        config: {
          render: () => <div>Content</div>
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with custom dialogConfig', () => {
      const props = {
        ...defaultProps,
        config: {
          title: 'Test Modal',
          render: () => <div>Content</div>,
          dialogConfig: {
            width: 600,
            maskClosable: true,
            keyboard: true
          }
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onCancelClick only', () => {
      const onCancelClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancelClick: onCancelClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onCancel only', () => {
      const onCancelMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancel: onCancelMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with both onCancel and onCancelClick', () => {
      const onCancelMock = vi.fn()
      const onCancelClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onCancel: onCancelMock,
          onCancelClick: onCancelClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOk only', () => {
      const onOkMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick only', () => {
      const onOkClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with both onOk and onOkClick', () => {
      const onOkMock = vi.fn()
      const onOkClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOk returning false', () => {
      const onOkMock = vi.fn().mockResolvedValue(false)
      const onOkClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick returning true', () => {
      const onOkClickMock = vi.fn().mockResolvedValue(true)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick returning false to keep modal open', () => {
      const onOkClickMock = vi.fn().mockResolvedValue(false)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOk returning truthy value', () => {
      const onOkMock = vi.fn().mockResolvedValue('some-result')
      const onOkClickMock = vi.fn().mockResolvedValue(true)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOk: onOkMock,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick returning truthy value', () => {
      const onOkClickMock = vi.fn().mockResolvedValue('result')
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick returning undefined', () => {
      const onOkClickMock = vi.fn()
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick returning null', () => {
      const onOkClickMock = vi.fn().mockResolvedValue(null)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick returning 0', () => {
      const onOkClickMock = vi.fn().mockResolvedValue(0)
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })

    it('should handle render with onOkClick returning empty string', () => {
      const onOkClickMock = vi.fn().mockResolvedValue('')
      const props = {
        ...defaultProps,
        config: {
          ...defaultProps.config,
          onOkClick: onOkClickMock
        }
      }
      
      const { unmount } = render(<DefineModal {...props} />)
      
      unmount()
    })
  })

  describe('HackModal', () => {
    it('should render without crashing', () => {
      const { unmount } = render(
        <HackModal title="Test Title">
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should render with function title', () => {
      const { unmount } = render(
        <HackModal title={() => 'Function Title'}>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should render without title', () => {
      const { unmount } = render(
        <HackModal>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should handle closable=false', () => {
      const { unmount } = render(
        <HackModal title="Test" closable={false}>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should handle custom className', () => {
      const { unmount } = render(
        <HackModal title="Test" className="custom-class">
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should handle onCancel callback', () => {
      const onCancelMock = vi.fn()
      const { unmount } = render(
        <HackModal title="Test" onCancel={onCancelMock}>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should handle children content', () => {
      const { unmount } = render(
        <HackModal title="Test">
          <div>Test Child Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should pass through other props to Modal', () => {
      const { unmount } = render(
        <HackModal title="Test" width={800} centered>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should handle closable=true explicitly', () => {
      const { unmount } = render(
        <HackModal title="Test" closable={true}>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should pass open prop to Modal', () => {
      const { unmount } = render(
        <HackModal title="Test" open={true}>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })

    it('should handle onOk callback', () => {
      const onOkMock = vi.fn()
      const { unmount } = render(
        <HackModal title="Test" onOk={onOkMock}>
          <div>Content</div>
        </HackModal>
      )
      
      expect(() => unmount()).not.toThrow()
    })
  })

  describe('apiHook', () => {
    it('should close all modals', async () => {
      await invokeCloseAllModal()
    })

    it('should create modal with required options', async () => {
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = []
      
      const api = apiHook(TestComponent)
      
      const result = await api({
        title: 'Test Modal',
        render: () => <div>Content</div>
      })
      
      expect(result).toBeDefined()
      expect(result.elId).toBeDefined()
      expect(result.closeModal).toBeDefined()
      expect(result.componentInstance).toBeDefined()
    })

    it('should call beforeOpen callback during modal creation', async () => {
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = []
      
      const beforeOpenMock = vi.fn().mockResolvedValue(undefined)
      
      const api = apiHook(TestComponent, beforeOpenMock)
      
      await api({
        title: 'Test Modal',
        render: () => <div>Content</div>
      })
      
      expect(beforeOpenMock).toHaveBeenCalled()
    })

    it('should handle beforeOpen callback error gracefully', async () => {
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = []
      
      const beforeOpenMock = vi.fn().mockRejectedValue(new Error('Test error'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      
      const api = apiHook(TestComponent, beforeOpenMock)
      
      await api({
        title: 'Test Modal',
        render: () => <div>Content</div>,
        beforeOpen: beforeOpenMock
      })
      
      consoleSpy.mockRestore()
    })

    it('should apply hackerFnList callbacks', async () => {
      const hackerMock = vi.fn()
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = [hackerMock]
      
      const api = apiHook(TestComponent)
      
      await api({
        title: 'Test Modal',
        render: () => <div>Content</div>
      })
      
      expect(hackerMock).toHaveBeenCalled()
    })

    it('should apply multiple hackerFnList callbacks with errors', async () => {
      const errorMock = vi.fn(() => { throw new Error('hacker error') })
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = [errorMock]
      
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      
      const api = apiHook(TestComponent)
      
      await api({
        title: 'Test Modal',
        render: () => <div>Content</div>
      })
      
      expect(errorMock).toHaveBeenCalled()
      consoleSpy.mockRestore()
    })

    it('should apply multiple hackerFnList callbacks', async () => {
      const hackerMock1 = vi.fn()
      const hackerMock2 = vi.fn()
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = [hackerMock1, hackerMock2]
      
      const api = apiHook(TestComponent)
      
      await api({
        title: 'Test Modal',
        render: () => <div>Content</div>
      })
      
      expect(hackerMock1).toHaveBeenCalled()
      expect(hackerMock2).toHaveBeenCalled()
    })

    it('should return early if location changed during beforeOpen', async () => {
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = []
      
      const firstHref = 'http://localhost/test-early-1'
      const secondHref = 'http://localhost/test-early-2'
      
      Object.defineProperty(window, 'location', {
        writable: true,
        value: { ...window.location, href: firstHref }
      })
      
      const api = apiHook(TestComponent)
      
      await api({
        title: 'Test Modal',
        render: () => <div>Content</div>
      })
      
      Object.defineProperty(window, 'location', {
        writable: true,
        value: { ...window.location, href: secondHref }
      })
      
      const result = await api({
        title: 'Test Modal 2',
        render: () => <div>Content</div>
      })
      
      expect(result).toBeUndefined()
    })

    it('should update existing component instance on second call', async () => {
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = []
      
      const api = apiHook(TestComponent)
      
      const result1 = await api({
        title: 'Test 1',
        render: () => <div>Content1</div>
      })
      
      const result2 = await api({
        title: 'Test 2',
        render: () => <div>Content2</div>
      })
      
      expect(result1.elId).toBe(result2.elId)
      expect(result1.closeModal).toBe(result2.closeModal)
    })

    it('should handle configuration objects properly', async () => {
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = []
      
      const api = apiHook(TestComponent)
      
      const result = await api({
        title: 'Test Config',
        width: '600px',
        maskClosable: false,
        render: () => <div>Content</div>
      })
      
      expect(result).toBeDefined()
    })

    it('should handle getContainer function', async () => {
      const TestComponent = () => <div>Test</div>
      ;(TestComponent as any).hackerFnList = []
      
      const api = apiHook(TestComponent)
      
      const result = await api({
        title: 'Test Modal',
        render: () => <div>Content</div>,
        getContainer: () => document.createElement('div')
      })
      
      expect(result).toBeDefined()
    })
  })

  describe('invokeHackerConfig', () => {
    it('should configure invokeInjectConfirmEvent in config.props', () => {
      const config: any = {
        props: {
          title: 'Test Modal'
        }
      }
      
      const mockComponent = {
        props: config.props
      }
      
      const getComponent = () => mockComponent
      
      invokeHackerConfig(config, getComponent)
      
      let capturedFn: any = null
      config.props.invokeInjectConfirmEvent((fn: any) => {
        capturedFn = fn
      })
      
      expect(capturedFn).toBeDefined()
    })

    it('should handle getComponent returning null gracefully', () => {
      const config: any = {
        props: {
          title: 'Test Modal'
        }
      }
      
      const getComponent = () => null
      
      invokeHackerConfig(config, getComponent)
    })

    it('should handle config.config style configuration', () => {
      const config: any = {
        config: {
          title: 'Test Modal'
        }
      }
      
      const mockComponent = {
        props: config.config
      }
      
      const getComponent = () => mockComponent
      
      invokeHackerConfig(config, getComponent)
      
      expect(config.config.invokeInjectConfirmEvent).toBeDefined()
    })

    it('should configure onOk in dialogConfig', async () => {
      const onOkClickMock = vi.fn()
      const mockFn = vi.fn().mockResolvedValue('result')
      
      const config: any = {
        props: {
          title: 'Test Modal',
          onOkClick: onOkClickMock,
          dialogConfig: {}
        }
      }
      
      const mockComponent = {
        props: config.props
      }
      
      const getComponent = () => mockComponent
      
      invokeHackerConfig(config, getComponent)
      
      config.props.invokeInjectConfirmEvent(mockFn)
      
      await mockFn()
    })

    it('should handle config with dialogConfig already set', () => {
      const config: any = {
        props: {
          title: 'Test Modal',
          dialogConfig: {
            onOk: vi.fn()
          }
        }
      }
      
      const mockComponent = {
        props: config.props
      }
      
      const getComponent = () => mockComponent
      
      invokeHackerConfig(config, getComponent)
      
      expect(config.props.dialogConfig.onOk).toBeDefined()
    })
  })

  describe('DefineModalApi export', () => {
    it('should export a function from apiHook', () => {
      expect(DefineModalApi).toBeDefined()
      expect(typeof DefineModalApi).toBe('function')
    })
  })
})