import re

with open('/var/lms/be/src/main/java/com/lms/material/controller/InstructorMaterialController.java', 'r') as f:
    content = f.read()

# Inject Repositories
if 'QuizQuestionRepository' not in content:
    content = content.replace(
        "private final MindmapRepository mindmapRepository;",
        "private final MindmapRepository mindmapRepository;\n    private final com.lms.material.repository.QuizQuestionRepository quizQuestionRepository;\n    private final com.lms.material.repository.QuizOptionRepository quizOptionRepository;\n    private final com.lms.material.repository.FlashcardRepository flashcardRepository;"
    )

# Fix versioningOverwrite
clone_logic = """
        // Clone specific material
        if (gen.getMaterialType() == com.lms.common.enums.MaterialType.QUIZ) {
            com.lms.material.entity.Quiz oldQuiz = quizRepository.findByMaterialGeneration_IdAndIsDeletedFalse(gen.getId()).orElse(null);
            if (oldQuiz != null) {
                com.lms.material.entity.Quiz newQuiz = new com.lms.material.entity.Quiz();
                newQuiz.setMaterialGeneration(newGen);
                newQuiz.setQuestionCount(oldQuiz.getQuestionCount());
                newQuiz.setIsOfficial(oldQuiz.getIsOfficial());
                newQuiz.setQuizType(oldQuiz.getQuizType());
                newQuiz.setAllowReview(oldQuiz.getAllowReview());
                newQuiz.setMaxAttempts(oldQuiz.getMaxAttempts());
                newQuiz.setDurationMinutes(oldQuiz.getDurationMinutes());
                quizRepository.save(newQuiz);
                newMaterialId = newQuiz.getId();
                
                // Deep clone QuizQuestions
                java.util.List<com.lms.material.entity.QuizQuestion> oldQuestions = quizQuestionRepository.findByQuiz_IdOrderByDisplayOrderAsc(oldQuiz.getId());
                for (com.lms.material.entity.QuizQuestion oldQ : oldQuestions) {
                    com.lms.material.entity.QuizQuestion newQ = new com.lms.material.entity.QuizQuestion();
                    newQ.setQuiz(newQuiz);
                    newQ.setContent(oldQ.getContent());
                    newQ.setIsMultipleChoice(oldQ.getIsMultipleChoice());
                    newQ.setDisplayOrder(oldQ.getDisplayOrder());
                    quizQuestionRepository.save(newQ);
                    
                    java.util.List<com.lms.material.entity.QuizOption> oldOptions = quizOptionRepository.findByQuestion_Id(oldQ.getId());
                    for (com.lms.material.entity.QuizOption oldOpt : oldOptions) {
                        com.lms.material.entity.QuizOption newOpt = new com.lms.material.entity.QuizOption();
                        newOpt.setQuestion(newQ);
                        newOpt.setContent(oldOpt.getContent());
                        newOpt.setIsCorrect(oldOpt.getIsCorrect());
                        quizOptionRepository.save(newOpt);
                    }
                }
            }
        } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.FLASHCARD) {
            FlashcardDeck oldDeck = flashcardDeckRepository.findByMaterialGeneration_Id(gen.getId()).orElse(null);
            if (oldDeck != null) {
                FlashcardDeck newDeck = new FlashcardDeck();
                newDeck.setMaterialGeneration(newGen);
                newDeck.setCardCount(oldDeck.getCardCount());
                newDeck.setIsOfficial(oldDeck.getIsOfficial());
                flashcardDeckRepository.save(newDeck);
                newMaterialId = newDeck.getId();
                
                // Deep clone Flashcards
                java.util.List<com.lms.material.entity.Flashcard> oldCards = flashcardRepository.findByFlashcardDeck_Id(oldDeck.getId());
                for (com.lms.material.entity.Flashcard oldCard : oldCards) {
                    com.lms.material.entity.Flashcard newCard = new com.lms.material.entity.Flashcard();
                    newCard.setFlashcardDeck(newDeck);
                    newCard.setFrontText(oldCard.getFrontText());
                    newCard.setBackText(oldCard.getBackText());
                    flashcardRepository.save(newCard);
                }
            }
        } else if (gen.getMaterialType() == com.lms.common.enums.MaterialType.MINDMAP) {
"""

content = re.sub(
    r"        // Clone specific material[\s\S]*?\} else if \(gen\.getMaterialType\(\) == com\.lms\.common\.enums\.MaterialType\.MINDMAP\) \{",
    clone_logic,
    content
)

# Set isDeleted = true on oldGen
content = content.replace(
    "materialAssignmentService.transferAssignments(id, newGen.getId());",
    "materialAssignmentService.transferAssignments(id, newGen.getId());\n        \n        // Archive V1\n        gen.setIsDeleted(true);\n        materialGenerationRepository.save(gen);"
)

with open('/var/lms/be/src/main/java/com/lms/material/controller/InstructorMaterialController.java', 'w') as f:
    f.write(content)

