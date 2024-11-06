package com.tudou.tudoumianshi.bloomFilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import java.util.List;
import java.util.Map;
@Slf4j
public class BlackIpUtils {


    private static BitMapBloomFilter bloomFilter;

    private static WordTree wordTree;
    //private static BitMapBloomFilter bloomFilter= new BitMapBloomFilter(100);

    // 判断 ip 是否在黑名单内
    public static boolean isBlackIp(String ip) {
        return bloomFilter.contains(ip);
    }



    public static boolean isIpInBlacklist(String ip) {
        // 查找IP地址是否匹配
        return wordTree.isMatch(ip);
    }

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
                WordTree word = new WordTree();
                for (String ip : blackIpList) {
                    word.addWord(ip);
                }
                wordTree = word;
            } else {
                wordTree = new WordTree();
            }
        }
    }

    // 重建 ip 黑名单
//    public static void rebuildBlackIp(String configInfo) {
//        if (StrUtil.isBlank(configInfo)) {
//            configInfo = "{}";
//        }
//        // 解析 yaml 文件
//        Yaml yaml = new Yaml();
//        Map map = yaml.loadAs(configInfo, Map.class);
//        // 获取 ip 黑名单
//        List<String> blackIpList = (List<String>) map.get("blackIpList");
//        // 加锁防止并发
//        synchronized (BlackIpUtils.class) {
//            if (CollectionUtil.isNotEmpty(blackIpList)) {
//                // 注意构造参数的设置
//                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
//                for (String ip : blackIpList) {
//                    bitMapBloomFilter.add(ip);
//                }
//                bloomFilter = bitMapBloomFilter;
//            } else {
//                bloomFilter = new BitMapBloomFilter(100);
//            }
//        }
//    }
}
