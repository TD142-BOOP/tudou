package com.tudou.tudoumianshi.bloomFilter;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import org.yaml.snakeyaml.Yaml;
import java.util.List;
import java.util.Map;

public class WordUtils {
    private static WordTree wordTree;


    // 判断 ip 是否在黑名单内
    public static boolean isBlackIp(String ip) {
        return wordTree.isMatch(ip);
    }


    // 重建 ip 黑名单
    public static void rebuildBlackIp(String configInfo) {
        if (StrUtil.isBlank(configInfo)) {
            configInfo = "{}";
        }
        // 解析 yaml 文件
        Yaml yaml = new Yaml();
        Map map = yaml.loadAs(configInfo, Map.class);
        // 获取 ip 黑名单
        List<String> blackIpList = (List<String>) map.get("blackIpList");
        // 加锁防止并发
        synchronized (BlackIpUtils.class) {
            if (CollectionUtil.isNotEmpty(blackIpList)) {
                // 注意构造参数的设置
                WordTree tree = new WordTree();
                for (String ip : blackIpList) {
                    tree.addWord(ip);
                }
                wordTree = tree;
            } else {
                wordTree = new WordTree();
            }
        }
    }
}
