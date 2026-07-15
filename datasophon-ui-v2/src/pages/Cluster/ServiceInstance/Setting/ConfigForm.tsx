/**
 * 服务配置动态表单渲染器。
 *
 * 移植自 datasophon-ui/src/components/Common/CommonTemplate/index.tsx。
 * 根据每个 ConfigField.type 渲染对应的 Pro 表单控件：
 *   input / password / slider / switch / select / multipleSelect /
 *   multiple(单值列表) / multipleWithKey(key-value 列表) / multipleWithMap(嵌套分组)
 *
 * 必须在 ProForm 内使用，表单值由外层 ProForm 管理。
 */

import {
  ProCard,
  ProFormGroup,
  ProFormList,
  ProFormSelect,
  ProFormSlider,
  ProFormSwitch,
  ProFormText,
} from '@ant-design/pro-components';
import { QuestionCircleOutlined } from '@ant-design/icons';
import { theme, Tooltip } from 'antd';
import React from 'react';

import { invokeMapShowMultiply } from './configTransform';

type ConfigField = DATASOPHON.ConfigField;

interface ConfigFormProps {
  /** 经 invokeHandleTemplateData 转换后的配置项列表 */
  templateData: ConfigField[];
  /** ProFormList 嵌套时传入上级 name 路径前缀 */
  namePrefix?: (string | number)[];
  className?: string;
}

const ConfigForm: React.FC<ConfigFormProps> = ({
  templateData,
  className = '',
  namePrefix,
}) => {
  const { token } = theme.useToken();

  const renderItem = (item: ConfigField): React.ReactNode => {
    // 配置名称、key 和说明图标固定为同一行，避免说明图标插在名称与 key 之间。
    const label: React.ReactNode = (
      <span className="inline-flex items-center gap-1 whitespace-nowrap">
        <span>{item.label}</span>
        {item.name && (
          <span className="text-[12px]" style={{ color: token.colorTextTertiary }}>
            {item.name}
          </span>
        )}
        {item.description && (
          <Tooltip title={item.description}>
            <QuestionCircleOutlined style={{ color: token.colorTextTertiary }} />
          </Tooltip>
        )}
      </span>
    );

    const fieldName = namePrefix
      ? [...namePrefix, item.name].filter(Boolean)
      : item.name;

    const commonProps = {
      label,
      name: fieldName,
      initialValue: item.value ?? item.defaultValue,
    };

    const requiredRule = {
      required: item.required,
      message: `${item.label}不能为空！`,
    };

    // ── 非列表型控件 ─────────────────────────────────────────────────────
    if (
      !['multipleWithKey', 'multipleWithMap'].includes(item.type) &&
      !invokeMapShowMultiply(item)
    ) {
      if (item.type === 'input') {
        return <ProFormText {...commonProps} rules={[requiredRule]} />;
      }
      if (item.type === 'password') {
        return <ProFormText.Password {...commonProps} rules={[requiredRule]} />;
      }
      if (item.type === 'slider') {
        const min = item.minValue ?? 0;
        const max = item.maxValue ?? 100;
        const fieldProps = {
          min,
          max,
          marks: { 0: String(min), [max]: String(max) },
        };
        return (
          <ProFormSlider
            {...commonProps}
            {...fieldProps}
            fieldProps={fieldProps}
            rules={[requiredRule]}
          />
        );
      }
      if (item.type === 'switch') {
        return <ProFormSwitch {...commonProps} />;
      }
      if (item.type === 'select' || item.type === 'multipleSelect') {
        return (
          <ProFormSelect
            {...commonProps}
            mode={item.type === 'multipleSelect' ? 'multiple' : undefined}
            options={(item.selectValue ?? []).map((val) => ({
              label: val,
              value: val,
            }))}
            rules={[requiredRule]}
          />
        );
      }
      return null;
    }

    // ── multiple / input+stringArray：单值列表 ────────────────────────────
    if (invokeMapShowMultiply(item)) {
      return (
        <ProFormList
          {...commonProps}
          className="w-full"
          rules={[
            {
              required: item.required,
              validator: async (_, value) => {
                if (!item.required) return;
                const joined = ((value ?? []) as { value: unknown }[])
                  .map((v) => v.value)
                  .join('');
                if (joined && joined.length > 0) return;
                throw new Error('至少要有一项！');
              },
            },
          ]}
        >
          <ProFormGroup key="group">
            <ProFormText
              width="xl"
              name="value"
              rules={[
                {
                  required: item.required,
                  whitespace: true,
                  message: `${item.label}不能为空！`,
                },
              ]}
            />
          </ProFormGroup>
        </ProFormList>
      );
    }

    // ── multipleWithKey：key-value 列表 ──────────────────────────────────
    if (item.type === 'multipleWithKey') {
      return (
        <ProFormList
          {...commonProps}
          rules={[
            {
              required: item.required,
              validator: async (_, value) => {
                if (!item.required || (value && value.length > 0)) return;
                throw new Error('至少要有一项！');
              },
            },
          ]}
        >
          <ProFormGroup key="group">
            <ProFormText
              width="md"
              name="key"
              rules={[
                {
                  required: item.required,
                  message: `${item.label} key 不能为空！`,
                },
              ]}
            />
            <ProFormText
              width="md"
              name="value"
              rules={[
                {
                  required: item.required,
                  message: `${item.label} value 不能为空！`,
                },
              ]}
            />
          </ProFormGroup>
        </ProFormList>
      );
    }

    // ── multipleWithMap：嵌套分组（key 锁死只改 value） ──────────────────
    if (item.type === 'multipleWithMap') {
      let creatorRecord: { items: { key: string; value: unknown }[] } = {
        items: [],
      };
      const firstVal = (
        item.value as { items: { key: string; value: unknown }[] }[]
      )?.[0];
      if (
        firstVal &&
        typeof firstVal === 'object' &&
        Array.isArray(firstVal.items)
      ) {
        creatorRecord = {
          items: firstVal.items.map((kv) => ({ key: kv.key, value: '' })),
        };
      }

      return (
        <ProFormList
          {...commonProps}
          rules={[
            {
              required: item.required,
              validator: async (_, value) => {
                if (!item.required || (value && value.length > 0)) return;
                throw new Error('至少要有一项！');
              },
            },
          ]}
          itemRender={({ listDom, action }, { index }) => (
            <ProCard
              extra={action as React.ReactNode}
              title={`${index}.${item.label}_配置`}
              style={{ marginBlockEnd: 8, border: '1px solid #f0f0f0' }}
            >
              {listDom}
            </ProCard>
          )}
          creatorButtonProps={{ creatorButtonText: '新增配置组' }}
          creatorRecord={creatorRecord as any}
          deleteIconProps={{ tooltipText: '删除该配置组' }}
        >
          <ProFormList
            name="items"
            creatorButtonProps={false}
            copyIconProps={false}
            deleteIconProps={false}
          >
            <ProFormGroup key="group">
              <ProFormText width="md" name="key" disabled />
              <ProFormText
                width="md"
                name="value"
                rules={[
                  {
                    required: item.required,
                    whitespace: true,
                    message: '不能为空！',
                  },
                ]}
              />
            </ProFormGroup>
          </ProFormList>
        </ProFormList>
      );
    }

    return null;
  };

  return (
    <div className={className}>
      {templateData
        .filter((item) => item?.enabled !== false)
        .map((item) => {
          const fieldName = namePrefix
            ? [...namePrefix, item.name].filter(Boolean)
            : item.name;
          const fieldKey = Array.isArray(fieldName)
            ? fieldName.join('.')
            : (fieldName ?? item.name);
          const content = renderItem(item);
          if (!content) return null;
          return <React.Fragment key={fieldKey}>{content}</React.Fragment>;
        })}
    </div>
  );
};

export default ConfigForm;
