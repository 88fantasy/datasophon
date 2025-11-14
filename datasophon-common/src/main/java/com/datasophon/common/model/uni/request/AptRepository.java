package com.datasophon.common.model.uni.request;

import lombok.Data;

@Data
public class AptRepository {
        private String name;
        private Boolean online = true;
        private Storage storage = new Storage();
        private Component component = new Component();
        private Apt apt;
        private AptSigning aptSigning;

        @Data
        public static class Apt {
                private String distribution;
        }

        @Data
        public static class AptSigning {
                private String keypair;
                private String passphrase;
        }
}
