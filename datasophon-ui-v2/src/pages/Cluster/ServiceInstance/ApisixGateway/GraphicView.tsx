import { Alert, Menu } from 'antd';
import React, { useMemo, useState } from 'react';
import { useIntl } from '@umijs/max';
import GlobalRuleTable from './GlobalRuleTable';
import RouteTable from './RouteTable';
import UpstreamTable from './UpstreamTable';
import type { ApisixGatewayDoc } from './gatewayYaml';

const KNOWN_TOP_LEVEL_KEYS = ['upstreams', 'routes', 'global_rules'];

type ResourceType = 'routes' | 'upstreams' | 'globalRules';

interface GraphicViewProps {
  doc: ApisixGatewayDoc;
  onChange: (next: ApisixGatewayDoc) => void;
}

const GraphicView: React.FC<GraphicViewProps> = ({ doc, onChange }) => {
  const intl = useIntl();
  const [resource, setResource] = useState<ResourceType>('routes');

  const unknownSegmentCount = useMemo(
    () => Object.keys(doc).filter((key) => !KNOWN_TOP_LEVEL_KEYS.includes(key)).length,
    [doc],
  );

  const menuItems = [
    {
      key: 'routes',
      label: intl.formatMessage({ id: 'pages.apisixGateway.resource.routes' }),
    },
    {
      key: 'upstreams',
      label: intl.formatMessage({ id: 'pages.apisixGateway.resource.upstreams' }),
    },
    {
      key: 'globalRules',
      label: intl.formatMessage({ id: 'pages.apisixGateway.resource.globalRules' }),
    },
  ];

  return (
    <div>
      {unknownSegmentCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 8 }}
          message={intl.formatMessage(
            { id: 'pages.apisixGateway.unknownSegmentsHint' },
            { count: unknownSegmentCount },
          )}
        />
      )}
      <div style={{ display: 'flex', gap: 16 }}>
        <Menu
          mode="vertical"
          selectedKeys={[resource]}
          items={menuItems}
          onClick={({ key }) => setResource(key as ResourceType)}
          style={{ width: 160, flexShrink: 0 }}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          {resource === 'routes' && <RouteTable doc={doc} onChange={onChange} />}
          {resource === 'upstreams' && <UpstreamTable doc={doc} onChange={onChange} />}
          {resource === 'globalRules' && (
            <GlobalRuleTable doc={doc} onChange={onChange} />
          )}
        </div>
      </div>
    </div>
  );
};

export default GraphicView;
