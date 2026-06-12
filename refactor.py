import os
import re
from pathlib import Path

BASE_DIR = Path('/var/www/interview-trainer/backend/src/main/java/com/asknehru/interviewsimulator')
BASE_PKG = 'com.asknehru.interviewsimulator'

# Module assignments
MODULE_MAPPING = {
    # auth
    'User': 'auth',
    'UserDetailsServiceImpl': 'auth',
    'UserRepository': 'auth',
    'AuthResponse': 'auth',
    'LoginRequest': 'auth',
    'RegisterRequest': 'auth',
    'JwtAuthenticationFilter': 'auth',
    'JwtUtils': 'auth',
    'SecurityConfig': 'auth',
    
    # candidate
    'CandidateProfile': 'candidate',
    'UserAspiration': 'candidate',
    'AspirationChecklist': 'candidate',
    'UserAspirationController': 'candidate',
    'UserProgressController': 'candidate',
    'UserAspirationService': 'candidate',
    'UserProgressService': 'candidate',
    'CandidateProfileRepository': 'candidate',
    'UserAspirationRepository': 'candidate',
    'AspirationChecklistRepository': 'candidate',
    
    # document
    'Document': 'document',
    'DocumentCategory': 'document',
    'JobDescriptionAnalysis': 'document',
    'JobDescriptionController': 'document',
    'DocumentService': 'document',
    'JobDescriptionAnalyzerService': 'document',
    'DocumentRepository': 'document',
    'DocumentCategoryRepository': 'document',
    'JobDescriptionAnalysisRepository': 'document',
    
    # interview
    'Interview': 'interview',
    'Question': 'interview',
    'Answer': 'interview',
    'Evaluation': 'interview',
    'InterviewController': 'interview',
    'InterviewService': 'interview',
    'QuestionGeneratorService': 'interview',
    'EvaluatorService': 'interview',
    'InterviewRepository': 'interview',
    'QuestionRepository': 'interview',
    'AnswerRepository': 'interview',
    'EvaluationRepository': 'interview',
    'StartInterviewRequest': 'interview',
    'SubmitAnswerRequest': 'interview',
    'GeneratedQuestion': 'interview',
    
    # test
    'McqTestController': 'test',
    'CodingTestController': 'test',
    'McqTestService': 'test',
    'CodingTestService': 'test',
    'StartMcqTestRequest': 'test',
    'SubmitMcqTestRequest': 'test',
    'StartCodingTestRequest': 'test',
    'SubmitCodingApproachRequest': 'test',
    'SubmitCodingCodeRequest': 'test',
    
    # hrvoice
    'HRVoiceInterviewSession': 'hrvoice',
    'HRVoiceInterviewTurn': 'hrvoice',
    'HRVoiceInterviewService': 'hrvoice',
    'HRVoiceInterviewSessionRepository': 'hrvoice',
    'HRVoiceInterviewTurnRepository': 'hrvoice',
    'ChatRequest': 'hrvoice',
    'ChatResponse': 'hrvoice',
    
    # syllabus
    'Syllabus': 'syllabus',
    'Topic': 'syllabus',
    'SyllabusExplanation': 'syllabus',
    'SyllabusController': 'syllabus',
    'TopicController': 'syllabus',
    'SyllabusService': 'syllabus',
    'SyllabusRepository': 'syllabus',
    'TopicRepository': 'syllabus',
    'SyllabusExplanationRepository': 'syllabus',
    
    # ai
    'LlmService': 'ai',
    
    # core
    'GeneratedContentCache': 'core',
    'GeneratedContentCacheRepository': 'core',
    'RootController': 'core',
}

# Find all java files
java_files = list(BASE_DIR.rglob('*.java'))

classes_info = {}

for f in java_files:
    if f.name == 'InterviewSimulatorApplication.java':
        continue
        
    class_name = f.stem
    old_rel_path = f.relative_to(BASE_DIR)
    
    # original package
    old_pkg = f"{BASE_PKG}.{old_rel_path.parent}".replace('/', '.')
    old_fqn = f"{old_pkg}.{class_name}"
    
    module = MODULE_MAPPING.get(class_name, 'core')
    
    is_dto = 'dto' in str(old_rel_path) or class_name.endswith('Request') or class_name.endswith('Response') or class_name == 'GeneratedQuestion'
    
    new_pkg = f"{BASE_PKG}.{module}.dto" if is_dto else f"{BASE_PKG}.{module}"
    new_fqn = f"{new_pkg}.{class_name}"
    
    new_rel_path = f"{module}/dto/{f.name}" if is_dto else f"{module}/{f.name}"
    new_path = BASE_DIR / new_rel_path
    
    classes_info[class_name] = {
        'file': f,
        'old_pkg': old_pkg,
        'old_fqn': old_fqn,
        'new_pkg': new_pkg,
        'new_fqn': new_fqn,
        'new_path': new_path,
        'module': module,
        'is_dto': is_dto
    }

print(f"Found {len(classes_info)} classes to refactor.")

# Process files
for class_name, info in classes_info.items():
    content = info['file'].read_text()
    
    # Replace package
    content = re.sub(r'^package\s+[\w\.]+;', f"package {info['new_pkg']};", content, flags=re.MULTILINE)
    
    # Replace explicit imports and references
    for other_class, other_info in classes_info.items():
        if class_name == other_class:
            continue
            
        # Replace explicit fully qualified names
        content = content.replace(other_info['old_fqn'], other_info['new_fqn'])
        
        # Add import if used and in different package
        if other_info['new_pkg'] != info['new_pkg']:
            # Check if class name is used as a whole word
            if re.search(r'\b' + other_class + r'\b', content):
                import_stmt = f"import {other_info['new_fqn']};"
                if import_stmt not in content:
                    # Insert after package declaration
                    content = re.sub(r'^(package\s+[\w\.]+;)', r'\1\n' + import_stmt, content, flags=re.MULTILINE)

    # Save to new path
    info['new_path'].parent.mkdir(parents=True, exist_ok=True)
    info['new_path'].write_text(content)

# Also update InterviewSimulatorApplication.java if needed
app_file = BASE_DIR / 'InterviewSimulatorApplication.java'
if app_file.exists():
    content = app_file.read_text()
    for other_class, other_info in classes_info.items():
        content = content.replace(other_info['old_fqn'], other_info['new_fqn'])
        if other_info['new_pkg'] != BASE_PKG:
            if re.search(r'\b' + other_class + r'\b', content):
                import_stmt = f"import {other_info['new_fqn']};"
                if import_stmt not in content:
                    content = re.sub(r'^(package\s+[\w\.]+;)', r'\1\n' + import_stmt, content, flags=re.MULTILINE)
    app_file.write_text(content)

print("Refactoring applied successfully.")
