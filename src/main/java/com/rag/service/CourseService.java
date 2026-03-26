package com.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.mapper.AiCourseMapper;
import com.rag.model.AiCourse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private final AiCourseMapper courseMapper;

    public CourseService(AiCourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    public List<AiCourse> recommendCourses(String userQuery, int limit) {
        if (userQuery == null || userQuery.isBlank()) return List.of();
        String q = userQuery.trim();

        List<AiCourse> all = courseMapper.selectList(
                new LambdaQueryWrapper<AiCourse>().eq(AiCourse::getStatus, 1)
        );
        if (all == null || all.isEmpty()) return List.of();

        List<CourseScore> scored = new ArrayList<>();
        for (AiCourse c : all) {
            int score = scoreCourse(q, c);
            if (score > 0) scored.add(new CourseScore(c, score));
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(CourseScore::score).reversed())
                .limit(limit)
                .map(CourseScore::course)
                .collect(Collectors.toList());
    }

    private int scoreCourse(String q, AiCourse c) {
        String keywords = c.getKeywords();
        if (keywords == null || keywords.isBlank()) return 0;
        int score = 0;
        String[] ks = keywords.split(",");
        for (String k : ks) {
            String kw = k.trim();
            if (kw.isEmpty()) continue;
            if (q.contains(kw)) score += 2;
        }
        // fallback: if category appears in query
        if (c.getCategory() != null && !c.getCategory().isBlank() && q.contains(c.getCategory().trim())) {
            score += 1;
        }
        return score;
    }

    private record CourseScore(AiCourse course, int score) {
    }
}

