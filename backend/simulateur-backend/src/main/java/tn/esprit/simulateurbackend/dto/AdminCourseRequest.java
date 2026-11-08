package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.simulateurbackend.entity.CourseLevel;

public  record AdminCourseRequest(

        @NotBlank(message = "Le titre est obligatoire.")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères.")
        String title,

        @NotBlank(message = "La description est obligatoire.")
        @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères.")
        String description,

        @NotNull(message = "Le niveau est obligatoire.")
        CourseLevel level,

        @NotBlank(message = "La catégorie est obligatoire.")
        @Size(max = 60, message = "La catégorie ne doit pas dépasser 60 caractères.")
        String category,

        @NotNull(message = "L'ordre est obligatoire.")
        @Min(value = 0, message = "L'ordre ne peut pas être négatif.")
        Integer displayOrder,

        @NotNull(message = "Le statut de publication est obligatoire.")
        Boolean published

) {
}