import { useState, useRef, useCallback } from 'react';
import { uploadEvidence, getErrorMessage } from '../services/api';

const ACCEPTED_TYPES = ['image/png', 'application/pdf'];
const ACCEPTED_EXTENSIONS = '.pdf,.png';
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function EvidenceUploader({ transactionId, onUploadSuccess }) {
  const [dragOver, setDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState(null);
  const [messageType, setMessageType] = useState('success');
  const [pendingFiles, setPendingFiles] = useState([]);
  const inputRef = useRef(null);

  const validateFile = useCallback((file) => {
    if (!file) return 'No se seleccionó ningún archivo.';

    if (!ACCEPTED_TYPES.includes(file.type)) {
      return `Tipo de archivo no válido para "${file.name}". Solo se permiten PDF y PNG.`;
    }

    if (file.size > MAX_FILE_SIZE) {
      return `"${file.name}" excede el límite de ${formatFileSize(MAX_FILE_SIZE)}.`;
    }

    if (file.size === 0) {
      return `"${file.name}" está vacío.`;
    }

    return null;
  }, []);

  const addFilesToList = useCallback((fileList) => {
    setMessage(null);
    const files = Array.from(fileList);
    const validFiles = [];
    const errors = [];

    files.forEach((file) => {
      const error = validateFile(file);
      if (error) {
        errors.push(error);
      } else {
        validFiles.push(file);
      }
    });

    if (errors.length > 0) {
      setMessage(errors.join(' | '));
      setMessageType('error');
    }

    if (validFiles.length > 0) {
      setPendingFiles((prev) => {
        // Avoid duplicates by name
        const existingNames = new Set(prev.map((f) => f.name));
        const newFiles = validFiles.filter((f) => !existingNames.has(f.name));
        return [...prev, ...newFiles];
      });
    }
  }, [validateFile]);

  const removeFile = useCallback((fileName) => {
    setPendingFiles((prev) => prev.filter((f) => f.name !== fileName));
  }, []);

  const handleDrop = useCallback((e) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files?.length > 0) {
      addFilesToList(e.dataTransfer.files);
    }
  }, [addFilesToList]);

  const handleDragOver = useCallback((e) => {
    e.preventDefault();
    setDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e) => {
    e.preventDefault();
    setDragOver(false);
  }, []);

  const handleInputChange = useCallback((e) => {
    if (e.target.files?.length > 0) {
      addFilesToList(e.target.files);
    }
    e.target.value = ''; // reset para permitir el mismo archivo de nuevo
  }, [addFilesToList]);

  const handleClick = useCallback(() => {
    inputRef.current?.click();
  }, []);

  const handleConfirmUpload = useCallback(async () => {
    if (pendingFiles.length === 0) return;

    setMessage(null);
    setUploading(true);

    const uploadedNames = [];
    const uploadErrors = [];

    for (const file of pendingFiles) {
      try {
        const result = await uploadEvidence(transactionId, file);
        uploadedNames.push(result.blobName || file.name);
      } catch (err) {
        uploadErrors.push(`"${file.name}": ${getErrorMessage(err)}`);
      }
    }

    setUploading(false);

    if (uploadErrors.length === 0) {
      setMessage(`Evidencias subidas correctamente: ${uploadedNames.join(', ')}`);
      setMessageType('success');
      setPendingFiles([]);
      onUploadSuccess?.();
    } else if (uploadedNames.length > 0) {
      setMessage(`Subidas: ${uploadedNames.join(', ')} | Errores: ${uploadErrors.join(' | ')}`);
      setMessageType('error');
      setPendingFiles([]);
      onUploadSuccess?.();
    } else {
      setMessage(`Error al subir los archivos: ${uploadErrors.join(' | ')}`);
      setMessageType('error');
    }
  }, [pendingFiles, transactionId, onUploadSuccess]);

  return (
    <div className="panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Evidencias</p>
          <h3>Subir archivo</h3>
        </div>
      </div>

      <div
        className={`upload-zone${dragOver ? ' drag-over' : ''}${uploading ? ' uploading' : ''}`}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onClick={handleClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleClick(); }}
        aria-label="Haz clic o arrastra archivos para agregar evidencias"
      >
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED_EXTENSIONS}
          onChange={handleInputChange}
          multiple
          style={{ display: 'none' }}
        />

        {uploading ? (
          <div className="upload-status">
            <span className="upload-spinner" />
            <p>Subiendo evidencias...</p>
          </div>
        ) : (
          <div className="upload-prompt">
            <span className="upload-icon">📄</span>
            <p><strong>Haz clic</strong> o arrastra archivos aquí</p>
            <p className="upload-hints">
              Se aceptan archivos <strong>PDF</strong> y <strong>PNG</strong> (máx. {formatFileSize(MAX_FILE_SIZE)} cada uno)
            </p>
          </div>
        )}
      </div>

      {pendingFiles.length > 0 ? (
        <div className="pending-files">
          <p className="pending-files-title">Archivos pendientes ({pendingFiles.length})</p>
          <ul className="pending-files-list">
            {pendingFiles.map((file) => (
              <li key={file.name} className="pending-file-item">
                <span className="pending-file-info">
                  <span className="pending-file-name">{file.name}</span>
                  <span className="pending-file-size">{formatFileSize(file.size)}</span>
                </span>
                <button
                  type="button"
                  className="remove-file-button"
                  onClick={() => removeFile(file.name)}
                  disabled={uploading}
                  title="Eliminar archivo"
                  aria-label={`Eliminar ${file.name}`}
                >
                  ✕
                </button>
              </li>
            ))}
          </ul>
          <button
            type="button"
            className="confirm-upload-button"
            onClick={handleConfirmUpload}
            disabled={uploading}
          >
            {uploading ? 'Subiendo...' : `Confirmar subida (${pendingFiles.length})`}
          </button>
        </div>
      ) : null}

      {message ? (
        <div className={`message ${messageType}`}>
          {message}
        </div>
      ) : null}
    </div>
  );
}

export default EvidenceUploader;

