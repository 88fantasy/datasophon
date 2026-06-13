import {
  type ProFormInstance,
  ProFormItem,
  ProFormText,
  ProFormUploadButton,
  StepsForm,
} from '@ant-design/pro-components';
import { Button, Form, Modal, message, Progress, Tooltip, Upload } from 'antd';
import type { UploadRequestOption } from 'rc-upload/lib/interface';
import React, {
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import ClusterContext from '@/context/ClusterContext';
import {
  importComponent,
  queryImportProgress,
  queryMergeProgress,
  uploadDeployFile,
} from '@/services/datasophon/deploy';
import { chunkedUpload } from '@/utils/ChunkedUploader';

interface Props {
  open: boolean;
  onClose: () => void;
}

// ─── 步骤 1：上传配置文件 ─────────────────────────────────────────────────

interface Step1Props {
  clusterId: number;
}

const StepUploadMeta: React.FC<Step1Props> = ({ clusterId }) => {
  const form = Form.useFormInstance();
  const password = Form.useWatch('contentDecodePasswd', form);

  const customUpload = async (options: UploadRequestOption) => {
    try {
      const res = await uploadDeployFile(clusterId, options.file as File);
      options.onSuccess?.(res);
    } catch (err) {
      options.onError?.(err as Error);
    }
  };

  return (
    <>
      <ProFormText
        label="配置文件密码"
        name="contentDecodePasswd"
        rules={[{ required: true, message: '请输入配置文件密码' }]}
        fieldProps={{ placeholder: '请先输入解密密码' }}
      />
      <ProFormUploadButton
        label="配置文件"
        name="meteFileId"
        max={1}
        listType="text"
        title={!password ? '请先填写配置文件密码' : '选择并上传配置文件'}
        disabled={!password}
        fieldProps={{ customRequest: customUpload }}
        rules={[
          {
            required: true,
            validator: (_, value) => {
              if (!value?.length) return Promise.reject('请上传配置文件');
              const f = value[0];
              if (f.status === 'uploading')
                return Promise.reject('正在上传中，请稍后重试');
              if (f.status !== 'done')
                return Promise.reject('上传失败，请重新上传');
              return Promise.resolve();
            },
          },
        ]}
      />
    </>
  );
};

// ─── 步骤 2：上传部署包（分片）─────────────────────────────────────────────

const IMPORT_STATE_LABEL: Record<number, string> = {
  0: '初始化',
  2: '解析元数据',
  3: '解压安装包',
  4: '保存数据',
  5: '上传安装包到 Nexus',
  6: '上传镜像到 Nexus',
  7: '上传 Helm 包到 Nexus',
  1: '成功',
  '-1': '失败',
  '-2': '进度对象不存在',
};

interface Step2Props {
  clusterId: number;
}

const StepUploadPackage: React.FC<Step2Props> = ({ clusterId }) => {
  const form = Form.useFormInstance();
  const [uploadPercent, setUploadPercent] = useState(0);
  const [mergeProgress, setMergeProgress] =
    useState<DATASOPHON.MergeProgress | null>(null);
  const [fileName, setFileName] = useState('');
  const [uploading, setUploading] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const mergeTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );

  const cancelMergePolling = useCallback(() => {
    if (mergeTimerRef.current) {
      clearTimeout(mergeTimerRef.current);
      mergeTimerRef.current = undefined;
    }
  }, []);

  const pollMerge = useCallback(
    (attachId: number) => {
      cancelMergePolling();
      mergeTimerRef.current = setTimeout(async () => {
        try {
          const res = await queryMergeProgress(clusterId, attachId);
          if (res.data) {
            setMergeProgress(res.data);
            if ([0, 2].includes(res.data.state)) {
              // still in progress
              pollMerge(attachId);
            } else if (res.data.state === 1) {
              // merge complete
              setUploading(false);
              form.setFieldsValue({ pkgAttachId: attachId });
              message.success('部署包上传完成');
            } else {
              // error
              setUploading(false);
              message.error(`合并失败：${res.data.error ?? '未知错误'}`);
            }
          }
        } catch {
          cancelMergePolling();
          setUploading(false);
        }
      }, 1500);
    },
    [clusterId, form, cancelMergePolling],
  );

  useEffect(() => () => cancelMergePolling(), [cancelMergePolling]);

  const handleCustomUpload = async (options: UploadRequestOption) => {
    const file = options.file as File;
    setFileName(file.name);
    setUploadPercent(0);
    setMergeProgress(null);
    setUploading(true);
    form.setFieldsValue({ pkgAttachId: undefined });

    abortRef.current = new AbortController();
    try {
      const { attachId } = await chunkedUpload({
        clusterId,
        file,
        onProgress: setUploadPercent,
        signal: abortRef.current.signal,
      });
      options.onSuccess?.({ attachId });
      pollMerge(attachId);
    } catch (err: unknown) {
      const e = err as Error;
      if (e.name !== 'AbortError') {
        message.error(`上传失败：${e.message}`);
      }
      setUploading(false);
      options.onError?.(e);
    }
  };

  const mergePercent =
    mergeProgress == null
      ? 0
      : mergeProgress.state === 1
        ? 100
        : Math.min(Math.floor((mergeProgress.progress ?? 0) * 100), 99);

  const mergeStatus =
    mergeProgress?.state === 1
      ? 'success'
      : mergeProgress?.state === -1
        ? 'exception'
        : 'active';

  return (
    <>
      {/* 隐藏字段：合并完成后 setFieldsValue 写入，供 onFinish 校验 */}
      <ProFormItem
        name="pkgAttachId"
        rules={[{ required: true, message: '请等待部署包上传并合并完成' }]}
      >
        <input type="hidden" />
      </ProFormItem>

      <Form.Item label="部署包">
        <Upload
          accept=".tar.gz,.tar,.zip,.gz"
          showUploadList={false}
          customRequest={handleCustomUpload}
          disabled={uploading}
        >
          <Button disabled={uploading}>
            {fileName ? `已选：${fileName}` : '选择部署包文件'}
          </Button>
        </Upload>
      </Form.Item>

      {uploadPercent > 0 && (
        <Form.Item label="上传进度">
          <Progress
            percent={uploadPercent}
            size="small"
            status={uploading ? 'active' : 'normal'}
          />
        </Form.Item>
      )}

      {mergeProgress !== null && (
        <Form.Item label="合并进度">
          <Progress percent={mergePercent} size="small" status={mergeStatus} />
          {mergeProgress.state > 1 && (
            <span style={{ fontSize: 12, color: '#666', marginLeft: 8 }}>
              {IMPORT_STATE_LABEL[mergeProgress.state] ??
                String(mergeProgress.state)}
            </span>
          )}
        </Form.Item>
      )}
    </>
  );
};

// ─── 步骤 3：导入安装组件 ─────────────────────────────────────────────────

interface Step3Props {
  clusterId: number;
  formMapRef: React.MutableRefObject<
    React.MutableRefObject<ProFormInstance | undefined>[]
  >;
}

const TOTAL_IMPORT_STATES = Object.keys(IMPORT_STATE_LABEL).filter(
  (k) => Number(k) >= 0,
).length;

const StepImportCmp: React.FC<Step3Props> = ({ clusterId, formMapRef }) => {
  const [progress, setProgress] =
    useState<DATASOPHON.ImportCompProgress | null>(null);
  const [importing, setImporting] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const cancelPolling = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  }, []);

  const pollProgress = useCallback(
    (progressId: number) => {
      cancelPolling();
      timerRef.current = setTimeout(async () => {
        try {
          const res = await queryImportProgress(clusterId, progressId);
          if (res.data) {
            setProgress(res.data);
            if (![1, -1, -2].includes(res.data.state)) {
              pollProgress(progressId);
            } else {
              setImporting(false);
            }
          }
        } catch {
          cancelPolling();
          setImporting(false);
        }
      }, 2000);
    },
    [clusterId, cancelPolling],
  );

  useEffect(() => () => cancelPolling(), [cancelPolling]);

  const handleImport = useCallback(async () => {
    const step1Form = formMapRef.current[0]?.current;
    const step2Form = formMapRef.current[1]?.current;
    if (!step1Form || !step2Form) return;

    const { meteFileId, contentDecodePasswd } = step1Form.getFieldsValue();
    const { pkgAttachId } = step2Form.getFieldsValue();

    const metaId = (
      meteFileId as Array<{ response?: { data?: { id?: number } } }>
    )?.[0]?.response?.data?.id;
    if (!metaId || !contentDecodePasswd) {
      message.warning('请先完成前两步');
      return;
    }

    setImporting(true);
    setProgress(null);

    try {
      const res = await importComponent(clusterId, {
        meteFileId: metaId,
        pkgFileId: pkgAttachId,
        contentDecodePasswd,
      });
      if (res.data?.progressId != null) {
        setProgress(res.data);
        pollProgress(res.data.progressId);
      } else {
        setImporting(false);
        message.error('导入启动失败，未获取到 progressId');
      }
    } catch {
      setImporting(false);
    }
  }, [clusterId, formMapRef, pollProgress]);

  const stepPercent =
    progress == null
      ? 0
      : progress.state === 1
        ? 100
        : progress.state < 0
          ? Math.max(
              0,
              ((progress.state < 0 ? 0 : progress.state) - 1) /
                TOTAL_IMPORT_STATES,
            ) * 100
          : ((progress.state - 1) / TOTAL_IMPORT_STATES) * 100;

  const dashStatus =
    progress?.state === 1
      ? 'success'
      : progress != null && progress.state < 0
        ? 'exception'
        : undefined;

  return (
    <div style={{ textAlign: 'center', padding: '16px 0' }}>
      <Progress
        type="dashboard"
        percent={Math.floor(stepPercent)}
        status={dashStatus}
        size={200}
        format={(p) => {
          if (progress?.state === 1) return '成功';
          if (progress != null && progress.state < 0) {
            return (
              <Tooltip
                title={progress.error ?? IMPORT_STATE_LABEL[progress.state]}
              >
                <span>失败</span>
              </Tooltip>
            );
          }
          return (
            <div>
              <div>{p}%</div>
              {progress && progress.state > 1 && (
                <div style={{ fontSize: 11, marginTop: 4 }}>
                  {IMPORT_STATE_LABEL[progress.state] ?? '处理中'}
                  {progress.progress > 0 && (
                    <span style={{ marginLeft: 6 }}>
                      {(progress.progress * 100).toFixed(1)}%
                    </span>
                  )}
                </div>
              )}
            </div>
          );
        }}
      />
      <div
        style={{
          marginTop: 16,
          display: 'flex',
          gap: 8,
          justifyContent: 'center',
        }}
      >
        <Button
          type="primary"
          loading={importing}
          disabled={importing}
          onClick={handleImport}
        >
          {importing ? '导入中…' : progress != null ? '重新导入' : '开始导入'}
        </Button>
      </div>
    </div>
  );
};

// ─── 主组件 ──────────────────────────────────────────────────────────────

const UploadPackageModal: React.FC<Props> = ({ open, onClose }) => {
  const ctx = useContext(ClusterContext);
  const clusterId = ctx?.clusterId ?? 0;
  const formMapRef = useRef<
    React.MutableRefObject<ProFormInstance | undefined>[]
  >([]);

  const [currentStep, setCurrentStep] = useState(0);

  const handleStep1Finish = async (values: Record<string, unknown>) => {
    const list = values.meteFileId as
      | Array<{ status: string; response?: { data?: { id?: number } } }>
      | undefined;
    const f = list?.[0];
    if (!f || f.status !== 'done' || !f.response?.data?.id) {
      message.warning('请等待配置文件上传完成');
      return false;
    }
    return true;
  };

  const handleStep2Finish = async (values: Record<string, unknown>) => {
    if (!values.pkgAttachId) {
      message.warning('请等待部署包上传并合并完成');
      return false;
    }
    return true;
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      title="上传部署包"
      width={640}
      destroyOnClose
      afterClose={() => setCurrentStep(0)}
    >
      <StepsForm
        formMapRef={formMapRef}
        current={currentStep}
        onCurrentChange={setCurrentStep}
        submitter={{
          render: (props) => {
            const { step, onSubmit, onPre } = props;
            // Step 3 (index 2) uses its own import button; hide StepsForm submitter
            if (step === 2) return null;
            return (
              <div
                style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}
              >
                {step > 0 && <Button onClick={onPre}>上一步</Button>}
                <Button type="primary" onClick={onSubmit}>
                  下一步
                </Button>
              </div>
            );
          },
        }}
      >
        <StepsForm.StepForm
          name="step1"
          title="上传配置文件"
          onFinish={handleStep1Finish}
        >
          <StepUploadMeta clusterId={clusterId} />
        </StepsForm.StepForm>

        <StepsForm.StepForm
          name="step2"
          title="上传部署包"
          onFinish={handleStep2Finish}
        >
          <StepUploadPackage clusterId={clusterId} />
        </StepsForm.StepForm>

        <StepsForm.StepForm name="step3" title="导入安装组件">
          <StepImportCmp clusterId={clusterId} formMapRef={formMapRef} />
        </StepsForm.StepForm>
      </StepsForm>
    </Modal>
  );
};

export default UploadPackageModal;
