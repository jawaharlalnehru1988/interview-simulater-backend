# Documents API Module

The Documents API module allows users to upload, organize, and manage their documents for interview preparation. Users can categorize documents by type, category, and age (new or old).

## Features

- **File Upload**: Upload documents (PDF, Word, Excel, Images, PowerPoint, Text, etc.)
- **Organization**: Organize documents by:
  - `documentType`: Type of document (pdf, word, excel, image, powerpoint, text, other)
  - `category`: Custom categories for grouping (e.g., "Interview Preparation", "Research", etc.)
  - `oldOrNew`: Mark documents as new or old
  - `name`: Document name for easy identification
- **Filtering & Search**: Filter documents by type, category, and age
- **Statistics**: View document organization statistics
- **File Management**: Delete, update metadata, and download documents

## API Endpoints

### Base URL
```
/api/documents/
```

### 1. List All Documents
**Endpoint**: `GET /api/documents/documents/`

**Query Parameters**:
- `document_type`: Filter by document type (pdf, word, excel, image, powerpoint, text, other)
- `category`: Filter by category name
- `old_or_new`: Filter by age (new, old)
- `created_at__gte`: Filter by creation date (greater than or equal)
- `created_at__lte`: Filter by creation date (less than or equal)

**Response**:
```json
{
  "count": 5,
  "next": null,
  "previous": null,
  "results": [
    {
      "id": 1,
      "name": "Interview Questions.pdf",
      "document_type": "pdf",
      "document_type_display": "PDF",
      "category": "Interview Preparation",
      "old_or_new": "new",
      "old_or_new_display": "New",
      "file": "/media/documents/2024/01/15/file.pdf",
      "file_url": "http://localhost:8000/media/documents/2024/01/15/file.pdf",
      "description": "Important interview questions",
      "created_at": "2024-01-15T10:30:00Z",
      "updated_at": "2024-01-15T10:30:00Z"
    }
  ]
}
```

### 2. Upload a Document
**Endpoint**: `POST /api/documents/documents/` or `POST /api/documents/documents/upload/`

**Content-Type**: `multipart/form-data`

**Request Body**:
```json
{
  "name": "Interview Questions",
  "document_type": "pdf",
  "category": "Interview Preparation",
  "old_or_new": "new",
  "file": <binary file>,
  "description": "Important questions for the interview"
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8000/api/documents/documents/ \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "name=Interview Questions" \
  -F "document_type=pdf" \
  -F "category=Interview Preparation" \
  -F "old_or_new=new" \
  -F "file=@/path/to/file.pdf" \
  -F "description=Important questions"
```

**Response** (201 Created):
Same as single document response above.

### 3. Get Document Details
**Endpoint**: `GET /api/documents/documents/{id}/`

**Response**: Single document object

### 4. Update Document Metadata
**Endpoint**: `PATCH /api/documents/documents/{id}/`

**Request Body** (all fields optional):
```json
{
  "name": "Updated Name",
  "category": "New Category",
  "old_or_new": "old",
  "description": "Updated description"
}
```

### 5. Delete Document
**Endpoint**: `DELETE /api/documents/documents/{id}/`

**Response** (204 No Content)

### 6. Get Document Categories
**Endpoint**: `GET /api/documents/documents/categories/`

**Response**:
```json
{
  "categories": [
    "Interview Preparation",
    "Research",
    "Project Documentation"
  ]
}
```

### 7. Get Available Document Types
**Endpoint**: `GET /api/documents/documents/document_types/`

**Response**:
```json
{
  "document_types": [
    ["pdf", "PDF"],
    ["word", "Word Document"],
    ["excel", "Excel Spreadsheet"],
    ["image", "Image"],
    ["powerpoint", "PowerPoint"],
    ["text", "Text File"],
    ["other", "Other"]
  ]
}
```

### 8. Get Old/New Choices
**Endpoint**: `GET /api/documents/documents/old_or_new_choices/`

**Response**:
```json
{
  "old_or_new_choices": [
    ["new", "New"],
    ["old", "Old"]
  ]
}
```

### 9. Get Document Statistics
**Endpoint**: `GET /api/documents/documents/statistics/`

**Response**:
```json
{
  "total_documents": 25,
  "by_type": {
    "PDF": 10,
    "Word Document": 5,
    "Excel Spreadsheet": 3,
    "Image": 4,
    "PowerPoint": 2,
    "Text File": 1,
    "Other": 0
  },
  "by_category": {
    "Interview Preparation": 10,
    "Research": 8,
    "Project Documentation": 7
  },
  "by_age": {
    "new": 15,
    "old": 10
  }
}
```

### 10. Get Download URL
**Endpoint**: `GET /api/documents/documents/{id}/download/`

**Response**: Returns document details including file_url for direct access

## Document Types

- `pdf`: PDF documents
- `word`: Microsoft Word documents (.doc, .docx)
- `excel`: Excel spreadsheets (.xls, .xlsx)
- `image`: Image files (.jpg, .png, .gif, etc.)
- `powerpoint`: PowerPoint presentations (.ppt, .pptx)
- `text`: Text files (.txt)
- `other`: Other file types

## File Upload Constraints

- **Maximum File Size**: 50MB
- **Supported Formats**: Any format (validation is on size only)
- **File Storage**: Files are stored in `media/documents/{year}/{month}/{day}/` directory

## Authentication

All endpoints require JWT authentication. Include the token in the Authorization header:
```
Authorization: Bearer YOUR_JWT_TOKEN
```

## Filtering Examples

### Filter by PDF documents in Interview Preparation category
```
GET /api/documents/documents/?document_type=pdf&category=Interview%20Preparation
```

### Filter by new documents only
```
GET /api/documents/documents/?old_or_new=new
```

### Filter by documents created in January 2024
```
GET /api/documents/documents/?created_at__gte=2024-01-01&created_at__lte=2024-01-31
```

## Error Responses

### 400 Bad Request
```json
{
  "field": ["Error message describing the issue"]
}
```

### 401 Unauthorized
```json
{
  "detail": "Authentication credentials were not provided."
}
```

### 404 Not Found
```json
{
  "detail": "Not found."
}
```

### 413 Payload Too Large
```json
{
  "file": ["File size exceeds 50MB limit. Current size: 75.50MB"]
}
```

## Pagination

By default, results are paginated. To get all results or change pagination:
- Add `?limit=100` to get up to 100 results per page
- Use `?offset=0` to get specific pages

## Search

Currently, documents are filtered by structured fields. For text search within document content, consider implementing full-text search later.

## Example Usage

### Python with requests library:
```python
import requests

# Upload a document
url = 'http://localhost:8000/api/documents/documents/'
headers = {'Authorization': 'Bearer YOUR_TOKEN'}

with open('interview_questions.pdf', 'rb') as f:
    files = {
        'file': f,
        'name': 'Interview Questions',
        'document_type': 'pdf',
        'category': 'Interview Preparation',
        'old_or_new': 'new',
        'description': 'Important questions'
    }
    response = requests.post(url, headers=headers, files=files)
    print(response.json())

# Get all documents
response = requests.get(url, headers=headers)
print(response.json())

# Get statistics
response = requests.get(
    'http://localhost:8000/api/documents/documents/statistics/',
    headers=headers
)
print(response.json())
```

### JavaScript with fetch:
```javascript
// Upload a document
const formData = new FormData();
formData.append('name', 'Interview Questions');
formData.append('document_type', 'pdf');
formData.append('category', 'Interview Preparation');
formData.append('old_or_new', 'new');
formData.append('file', fileInput.files[0]);

const response = await fetch('http://localhost:8000/api/documents/documents/', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`
  },
  body: formData
});

const data = await response.json();
console.log(data);
```

## Installation & Setup

1. Add `django-filter` to requirements.txt (already done)
2. Run migrations:
   ```
   python manage.py migrate
   ```
3. Restart the Django development server
4. Access the admin panel at `/admin/` to manage documents if needed

## Future Enhancements

- Full-text search within documents
- Document versioning
- Document sharing with other users
- OCR (Optical Character Recognition) for scanned documents
- Document tagging system
- Bulk upload/delete operations
- Document preview functionality
