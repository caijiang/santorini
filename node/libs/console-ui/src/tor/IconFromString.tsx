import * as Icons from '@ant-design/icons';
import * as React from 'react';

interface IconProps {
  name: string;
}

const IconFromString: React.FC<IconProps> = ({ name }) => {
  const AntIcon = (Icons as any)[name];
  return AntIcon ? <AntIcon /> : null;
};

export default IconFromString;
