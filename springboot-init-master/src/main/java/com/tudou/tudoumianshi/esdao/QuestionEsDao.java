package com.tudou.tudoumianshi.esdao;


import com.tudou.tudoumianshi.model.dto.question.QuestionEsDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;


/**
 * 帖子 ES 操作
 *

 */

public interface QuestionEsDao extends ElasticsearchRepository<QuestionEsDTO, Long> {
    List<QuestionEsDTO> findByUserId(Long userId);
}