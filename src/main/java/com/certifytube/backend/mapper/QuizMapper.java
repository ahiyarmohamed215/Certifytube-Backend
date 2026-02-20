package com.certifytube.backend.mapper;

import com.certifytube.backend.dto.QuizQuestionDto;
import com.certifytube.backend.dto.QuizResponse;
import com.certifytube.backend.model.Quiz;
import com.certifytube.backend.model.QuizQuestion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuizMapper {

    QuizResponse toResponse(Quiz quiz, List<QuizQuestionDto> questions);

    @Mapping(source = "questionUid", target = "questionId")
    @Mapping(target = "options", ignore = true)
    QuizQuestionDto toQuestionDto(QuizQuestion question);
}
