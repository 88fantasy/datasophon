/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.controller;

import com.datasophon.api.configuration.AiProperties;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent")
public class AgentToolController {
    
    @Autowired
    private AiProperties aiProperties;
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private ClusterHostService clusterHostService;
    
    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;
    
    @GetMapping("/clusters")
    public List<ClusterInfoEntity> clusters(HttpServletRequest req,
                                            HttpServletResponse resp) {
        if (!isAuthorized(req)) {
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            return List.of();
        }
        return clusterInfoService.getClusterList();
    }
    
    @GetMapping("/clusters/{id}/hosts")
    public List<ClusterHostDO> hosts(@PathVariable Integer id,
                                     HttpServletRequest req,
                                     HttpServletResponse resp) {
        if (!isAuthorized(req)) {
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            return List.of();
        }
        return clusterHostService.getHostListByClusterId(id);
    }
    
    @GetMapping("/clusters/{id}/services")
    public List<ClusterServiceInstanceEntity> services(@PathVariable Integer id,
                                                       HttpServletRequest req,
                                                       HttpServletResponse resp) {
        if (!isAuthorized(req)) {
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            return List.of();
        }
        return clusterServiceInstanceService.listAll(id);
    }
    
    private boolean isAuthorized(HttpServletRequest req) {
        String token = req.getHeader("X-Agent-Token");
        return aiProperties.getInternalToken().equals(token);
    }
}
