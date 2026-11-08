package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.Course;

import java.util.List;

public interface CourseRepository
        extends JpaRepository<Course, Long> {

    List<Course> findByPublishedTrueOrderByDisplayOrderAsc();

    // Vue admin : toutes les formations, publiées ou non.
    List<Course> findAllByOrderByDisplayOrderAsc();
}