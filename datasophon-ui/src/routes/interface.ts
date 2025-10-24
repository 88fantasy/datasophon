import type { ItemType } from "antd/es/breadcrumb/Breadcrumb";
import type { JSX } from "react";
import type { RouteObject } from "react-router-dom";

export type TExendsRouteObject = Partial<
  RouteObject &
    ItemType & {
      linkPath?: string;
      component?: string;
      icon?: JSX.Element;
    }
>;
