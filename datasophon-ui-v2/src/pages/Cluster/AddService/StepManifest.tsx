import { ProFormText, ProFormUploadButton } from '@ant-design/pro-components';
import type { UploadRequestOption } from 'rc-upload/lib/interface';
import type React from 'react';
import { uploadDeployFile } from '@/services/datasophon/deploy';

interface Props {
  clusterId: number;
}

/** 步骤 1：上传部署清单 + 解密密码（复用切片 7a 的 /deploy/upload）。 */
const StepManifest: React.FC<Props> = ({ clusterId }) => {
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
      <ProFormUploadButton
        label="部署清单"
        name="deployFile"
        max={1}
        listType="text"
        title="选择并上传部署清单"
        fieldProps={{ customRequest: customUpload }}
        rules={[
          {
            required: true,
            validator: (_, value) => {
              if (!value?.length) return Promise.reject('请上传部署清单');
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
      <ProFormText
        label="配置文件密码"
        name="contentDecodePasswd"
        fieldProps={{
          placeholder: '如清单内容已加密请输入解密密码，否则留空',
        }}
      />
    </>
  );
};

export default StepManifest;
