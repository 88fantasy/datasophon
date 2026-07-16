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

package com.datasophon.api.hook;

import static com.datasophon.common.enums.CommandType.INSTALL_SERVICE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datasophon.common.enums.HookType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleInfo;

import java.util.List;

import org.junit.jupiter.api.Test;

class ServiceHookDispatcherTest {

    @Test
    void dispatchesMatchedHookWithClusterIdFromRoles() {
        RecordingHook hook = hook("otelSchemaInit");
        ServiceHookDispatcher dispatcher = dispatcher(hook);
        ServiceNode node = node(hookConfig(HookType.POST_INSTALL, "otelSchemaInit", null));
        node.setMasterRoles(List.of(role(null)));
        node.setWorkerRoles(List.of(role(7)));

        dispatcher.dispatch(node, HookType.POST_INSTALL);

        assertEquals(1, hook.invocations);
        assertEquals(7, hook.context.getClusterId());
    }

    @Test
    void skipsHooksForOtherLifecycleType() {
        RecordingHook hook = hook("otelSchemaInit");
        ServiceHookDispatcher dispatcher = dispatcher(hook);

        dispatcher.dispatch(node(hookConfig(HookType.POST_INSTALL, "otelSchemaInit", null)), HookType.POST_START);

        assertEquals(0, hook.invocations);
    }

    @Test
    void ignoresUnknownAction() {
        ServiceHookDispatcher dispatcher = dispatcher();

        assertDoesNotThrow(() -> dispatcher.dispatch(
                node(hookConfig(HookType.POST_INSTALL, "unknown", null)), HookType.POST_INSTALL));
    }

    @Test
    void swallowsHookException() {
        RecordingHook hook = hook("otelSchemaInit");
        hook.throwException = true;
        ServiceHookDispatcher dispatcher = dispatcher(hook);

        assertDoesNotThrow(() -> dispatcher.dispatch(
                node(hookConfig(HookType.POST_INSTALL, "otelSchemaInit", null)), HookType.POST_INSTALL));
    }

    @Test
    void skipsDisabledCondition() {
        RecordingHook hook = hook("otelSchemaInit");
        ServiceHookDispatcher dispatcher = dispatcher(hook);

        dispatcher.dispatch(node(hookConfig(HookType.POST_INSTALL, "otelSchemaInit", "false")), HookType.POST_INSTALL);

        assertEquals(0, hook.invocations);
    }

    @Test
    void invokesHookWhenConditionIsBlank() {
        RecordingHook hook = hook("otelSchemaInit");
        ServiceHookDispatcher dispatcher = dispatcher(hook);

        dispatcher.dispatch(node(hookConfig(HookType.POST_INSTALL, "otelSchemaInit", "")), HookType.POST_INSTALL);

        assertEquals(1, hook.invocations);
    }

    @Test
    void skipsUnreadyHookAndContinuesWithNextHook() {
        RecordingHook unready = hook("unready");
        unready.ready = false;
        RecordingHook ready = hook("ready");
        ServiceHookDispatcher dispatcher = dispatcher(unready, ready);
        ServiceNode node = node(hookConfig(HookType.POST_INSTALL, "unready", null));
        node.setServiceHooks(List.of(
                hookConfig(HookType.POST_INSTALL, "unready", null),
                hookConfig(HookType.POST_INSTALL, "ready", null)));

        dispatcher.dispatch(node, HookType.POST_INSTALL);

        assertEquals(0, unready.invocations);
        assertEquals(1, ready.invocations);
    }

    @Test
    void invokesHookUsingDefaultReadinessImplementation() {
        DefaultReadyHook hook = new DefaultReadyHook();
        ServiceHookDispatcher dispatcher = dispatcher(hook);

        dispatcher.dispatch(node(hookConfig(HookType.POST_INSTALL, "defaultReady", null)), HookType.POST_INSTALL);

        assertEquals(1, hook.invocations);
    }

    private ServiceHookDispatcher dispatcher(ServiceHook... hooks) {
        ServiceHookDispatcher dispatcher = new ServiceHookDispatcher(List.of(hooks));
        dispatcher.initialize();
        return dispatcher;
    }

    private RecordingHook hook(String type) {
        return new RecordingHook(type);
    }

    private ServiceNode node(HookConfig hookConfig) {
        ServiceNode node = new ServiceNode();
        node.setServiceName("DORIS");
        node.setCommandId("command-1");
        node.setCommandType(INSTALL_SERVICE);
        node.setMasterRoles(List.of(role(5)));
        node.setServiceHooks(List.of(hookConfig));
        return node;
    }

    private ServiceRoleInfo role(Integer clusterId) {
        ServiceRoleInfo role = new ServiceRoleInfo();
        role.setRoleType(ServiceRoleType.MASTER);
        role.setClusterId(clusterId);
        return role;
    }

    private HookConfig hookConfig(HookType type, String action, String condition) {
        HookConfig config = new HookConfig();
        config.setType(type);
        config.setAction(action);
        config.setCondition(condition);
        return config;
    }

    private static class RecordingHook implements ServiceHook {

        private final String type;
        private int invocations;
        private ServiceHookContext context;
        private boolean throwException;
        private boolean ready = true;

        private RecordingHook(String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public boolean isReady(ServiceHookContext hookContext) {
            return ready;
        }

        @Override
        public void invoke(ServiceHookContext hookContext) {
            invocations++;
            context = hookContext;
            if (throwException) {
                throw new IllegalStateException("failed");
            }
        }
    }

    private static class DefaultReadyHook implements ServiceHook {

        private int invocations;

        @Override
        public String getType() {
            return "defaultReady";
        }

        @Override
        public void invoke(ServiceHookContext context) {
            invocations++;
        }
    }
}
