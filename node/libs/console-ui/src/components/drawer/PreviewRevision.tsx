import { ConflictFieldSolution } from '../../slices/serverSideApply';
import _ from 'lodash';
import JsonView from '@uiw/react-json-view';
import { vscodeTheme } from '@uiw/react-json-view/vscode';
import { Typography } from 'antd';
import * as React from 'react';

interface PreviewRevisionProps {
  type: 'current' | 'draft';
  solution?: ConflictFieldSolution;
  value?: any;
}

/**
 * 显示修订，可能是草稿的，也可能是当前的
 */
const PreviewRevision: React.FC<PreviewRevisionProps> = ({
  value,
  solution,
  type,
}) => {
  const deleteLine =
    (type == 'current' && solution == 'force') ||
    (type == 'draft' && (solution == 'abandon' || solution == 'merge'));
  return _.isObject(value) ? (
    <div
      style={{
        textDecorationLine: deleteLine ? 'line-through' : undefined,
        textDecorationColor: 'red',
      }}
    >
      <JsonView value={value} style={vscodeTheme} />
    </div>
  ) : (
    <Typography.Text delete={deleteLine}>
      {JSON.stringify(value)}
    </Typography.Text>
  );
};

export default PreviewRevision;
