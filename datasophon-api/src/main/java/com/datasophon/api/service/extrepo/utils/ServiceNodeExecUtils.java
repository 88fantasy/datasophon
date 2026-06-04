package com.datasophon.api.service.extrepo.utils;

import static com.datasophon.common.enums.CommandType.STOP_SERVICE;

import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.ServiceRoleInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;

/**
 * @author zhanghuangbin
 */
public class ServiceNodeExecUtils {
    
    public static List<Pair<String, List<ServiceRoleInfo>>> getSortedRoleInfo(CommandType type, List<ServiceRoleInfo> roles) {
        if (CollectionUtil.isEmpty(roles)) {
            return new ArrayList<>(0);
        }
        List<String> sortedRoles = getSortedRoleNames(type, roles);
        Map<String, List<ServiceRoleInfo>> map = new HashMap<>();
        for (ServiceRoleInfo role : roles) {
            // 保留列表的原有顺序
            map.computeIfAbsent(role.getServiceRoleName(), k -> new ArrayList<>()).add(role);
        }
        List<Pair<String, List<ServiceRoleInfo>>> result = new ArrayList<>();
        for (String sortedRoleName : sortedRoles) {
            result.add(new Pair<>(sortedRoleName, map.getOrDefault(sortedRoleName, new ArrayList<>(0))));
        }
        return result;
    }
    
    private static List<String> getSortedRoleNames(CommandType type, List<ServiceRoleInfo> roles) {
        List<SortedRole> tempRoles = roles.stream().map(SortedRole::new).collect(Collectors.toList());
        if (STOP_SERVICE.equals(type)) {
            tempRoles.sort(Comparator.comparing(SortedRole::getOrder).reversed());
        } else {
            tempRoles.sort(Comparator.comparing(SortedRole::getOrder));
        }
        List<String> sortedRoles = new ArrayList<>();
        for (SortedRole role : tempRoles) {
            if (!sortedRoles.contains(role.getServiceRoleName())) {
                sortedRoles.add(role.getServiceRoleName());
            }
        }
        return sortedRoles;
    }
    
    @Data
    private static class SortedRole {
        
        private String serviceRoleName;
        
        private int order;
        
        public SortedRole(ServiceRoleInfo role) {
            serviceRoleName = role.getServiceRoleName();
            order = role.getSortNum() == null ? Integer.MAX_VALUE : role.getSortNum();
        }
    }
}
