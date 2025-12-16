import * as React from 'react';
import { ServiceConfigData } from '../../apis/service';

interface ServiceUnitProps {
  data: ServiceConfigData;
}

/**
 * 服务单元组件
 * @constructor
 */
const ServiceUnit: React.FC<ServiceUnitProps> = ({ data }) => {
  return data.name;
};

export default ServiceUnit;
