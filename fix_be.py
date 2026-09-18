import re

with open('/var/lms/be/src/main/java/com/lms/material/controller/InstructorMaterialController.java', 'r') as f:
    content = f.read()

# Fix 1: Do not return floating assignments in the list
old_assignment_map = """                    if (assignment.getCourse() != null) {
                        assignmentMap.put("courseId", assignment.getCourse().getId());
                    }
                    assignments.add(assignmentMap);"""
new_assignment_map = """                    if (assignment.getCourse() != null) {
                        assignmentMap.put("courseId", assignment.getCourse().getId());
                    }
                    if (assignment.getLesson() != null || assignment.getChapter() != null) {
                        assignments.add(assignmentMap);
                    }"""
content = content.replace(old_assignment_map, new_assignment_map)

# Fix 2: Do not create floating assignments in versioningOverwrite
old_force_assign = """        // Force the assignment to the target
        materialAssignmentService.assignMaterial(newGen.getId(), gen.getCourse().getId(), targetChapterId, targetLessonId);"""
new_force_assign = """        // Force the assignment to the target ONLY if target is provided
        if (targetChapterId != null || targetLessonId != null) {
            materialAssignmentService.assignMaterial(newGen.getId(), gen.getCourse().getId(), targetChapterId, targetLessonId);
        }"""
content = content.replace(old_force_assign, new_force_assign)

with open('/var/lms/be/src/main/java/com/lms/material/controller/InstructorMaterialController.java', 'w') as f:
    f.write(content)

