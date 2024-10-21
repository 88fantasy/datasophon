package com.datasophon.common.utils;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class OsUtils {

    private static final Logger logger = LoggerFactory.getLogger(OsUtils.class);

    public static OsType getOs(String result) {
        List<String> lines = new ArrayList<>();
        if(StringUtils.isNoneEmpty()) {
            lines.addAll(Arrays.asList(result.split(System.lineSeparator())));
        }
        Optional<String> optionalOsName = lines.stream().filter(s -> s.startsWith("ID=")).findAny();
        Optional<String> optionalOsVersionID = lines.stream().filter(s -> s.startsWith("VERSION_ID=")).findAny();
        Optional<String> optionalOsVersion = lines.stream().filter(s -> s.startsWith("VERSION=")).findAny();
        Optional<String> prettyName = lines.stream().filter(s -> s.startsWith("PRETTY_NAME=")).findAny();
        logger.info("[source] os:{}, versionId:{}, version:{}", optionalOsName.get(), optionalOsVersionID.get(), optionalOsVersion.get());
        String osName = optionalOsName.get();
        String osVersion = "";
        String osDetail = "";
        OsType osType = OsType.OTHER;
        if (optionalOsName.isPresent()) {
            osName = optionalOsName.get().replace("\"", "").split("=")[1];
            osVersion = "";
            if ("openEuler".equals(osName)) {
                if (prettyName.isPresent()) {
                    osDetail = prettyName.get().replaceAll("\"", "").split("=")[1].replaceAll("\\(", "")
                            .replaceAll("\\)", "").replaceAll(" ", "-");
                }
            } else {
                if (optionalOsVersionID.isPresent()) {
                    // VERSION_ID="7"
                    osVersion = optionalOsVersionID.get().replace("\"", "").split("=")[1];
                } else if (optionalOsVersion.isPresent()) {
                    // ERSION="7 (Core)"
                    osVersion = optionalOsVersion.get().replaceAll("\"", "").split("=")[1].split("\\(")[0].trim();
                }
                osDetail = String.format("%s-%s",osName, osVersion);
            }

            osType = OsType.of(osDetail);
        }
        logger.info("[dist] os:{}, version:{}, osDetail:{}, osType:{}", osName, osVersion, osDetail, osType.getDesc());
        return osType;
    }

    public static ArchType getArch(String result) {
        logger.info("arch:{}", result);
        return ArchType.of(result);
    }
}
