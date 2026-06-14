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

package com.datasophon.api.controller.v2;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.CreateUserRequest;
import com.datasophon.api.dto.v2.UpdateUserRequest;
import com.datasophon.api.dto.v2.UserInfoResponse;
import com.datasophon.api.dto.v2.UserPageResponse;
import com.datasophon.api.service.UserInfoService;
import com.datasophon.api.utils.SecurityUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.UserInfoEntity;

import jakarta.validation.Valid;

import java.util.List;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 用户管理接口（平台登录账号 CRUD）。
 *
 * <p>复用现有 UserInfoService，返回 ApiResponse 标准信封。
 * 完整 URL 前缀：{@code /ddh/api/v2/user}
 */
@Slf4j
@RestController
@RequestMapping("/v2/user")
public class UserV2Controller extends ApiController {
    
    @Autowired
    private UserInfoService userInfoService;
    
    // ─── 分页用户列表 ─────────────────────────────────────────────
    
    @GetMapping("/page")
    public ApiResponse<UserPageResponse> list(
                                              @RequestParam(required = false) String username,
                                              @RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "20") Integer pageSize) {
        Result result = userInfoService.getUserListByPage(username, page, pageSize);
        @SuppressWarnings("unchecked")
        List<UserInfoEntity> entities = (List<UserInfoEntity>) result.getData();
        long total = ((Number) result.get(Constants.TOTAL)).longValue();
        return ApiResponse.ok(UserPageResponse.of(UserInfoResponse.fromList(entities), total));
    }
    
    // ─── 新建用户 ────────────────────────────────────────────────
    
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CreateUserRequest request) {
        Result result = userInfoService.createUser(request.toEntity());
        return result.isSuccess()
                ? ApiResponse.ok()
                : ApiResponse.fail(500, String.valueOf(result.getMsg()));
    }
    
    // ─── 编辑用户（不含密码，密码由独立接口修改）─────────────────
    
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
                                    @PathVariable Integer id,
                                    @Valid @RequestBody UpdateUserRequest request) {
        Result result = userInfoService.updateUser(request.toEntity(id));
        return result.isSuccess()
                ? ApiResponse.ok()
                : ApiResponse.fail(500, String.valueOf(result.getMsg()));
    }
    
    // ─── 重置密码 ────────────────────────────────────────────────
    
    @Data
    public static class ResetPasswordRequest {
        private String password;
    }
    
    @PostMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(
                                           @PathVariable Integer id,
                                           @RequestBody ResetPasswordRequest request) {
        Result result = userInfoService.resetPassword(id, request.getPassword());
        return result.isSuccess()
                ? ApiResponse.ok()
                : ApiResponse.fail(400, String.valueOf(result.getMsg()));
    }
    
    // ─── 批量删除用户（仅超管可操作）────────────────────────────
    
    @DeleteMapping
    public ApiResponse<Void> delete(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.fail(400, "请选择要删除的用户");
        }
        if (SecurityUtils.getAuthUser().getId() != 1) {
            return ApiResponse.fail(403, "无操作权限");
        }
        userInfoService.removeByIds(ids);
        return ApiResponse.ok();
    }
}
