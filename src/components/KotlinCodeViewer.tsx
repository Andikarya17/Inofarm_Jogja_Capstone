import React, { useState } from 'react';
import { KOTLIN_CODE_FILES, KotlinCodeFile } from '../utils/kotlinTemplates';
import { X, Copy, Check, Code2, Download, FileCode, Layers, Info } from 'lucide-react';

interface KotlinCodeViewerProps {
  isOpen: boolean;
  onClose: () => void;
}

export const KotlinCodeViewer: React.FC<KotlinCodeViewerProps> = ({
  isOpen,
  onClose,
}) => {
  const [selectedFile, setSelectedFile] = useState<KotlinCodeFile>(KOTLIN_CODE_FILES[0]);
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const element = document.createElement('a');
    const file = new Blob([selectedFile.code], { type: 'text/plain' });
    element.href = URL.createObjectURL(file);
    element.download = selectedFile.filename;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 sm:p-6 animate-fade-in">
      <div className="bg-[#121c2a] text-[#f8f9ff] w-full max-w-5xl h-[92vh] rounded-3xl shadow-2xl flex flex-col overflow-hidden border border-[#bbcabf]/30">
        {/* Top Header */}
        <div className="px-6 py-4 border-b border-[#27313f] flex items-center justify-between bg-[#1a2638]">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-[#006c49] text-white rounded-xl flex items-center justify-center">
              <Code2 className="w-5 h-5 text-[#4edea3]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base sm:text-lg font-bold text-white">
                  Source Code Kotlin &amp; Jetpack Compose
                </h3>
                <span className="text-[10px] uppercase font-bold bg-[#10b981]/20 text-[#4edea3] px-2 py-0.5 rounded-full border border-[#10b981]/30">
                  Android Native
                </span>
              </div>
              <p className="text-xs text-[#d9e3f6]/70">
                Implementasi 100% siap pakai untuk Android Studio sesuai desain Inofarm Jogja
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-full hover:bg-[#27313f] text-[#d9e3f6] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body: Sidebar + Code Editor */}
        <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
          {/* File Explorer Sidebar */}
          <div className="w-full md:w-72 bg-[#141e2e] border-r border-[#27313f] p-3 flex md:flex-col gap-1 overflow-x-auto md:overflow-y-auto shrink-0">
            <div className="text-[11px] font-bold text-[#d9e3f6]/60 uppercase tracking-wider px-2 py-1 hidden md:block">
              Daftar File Project
            </div>
            {KOTLIN_CODE_FILES.map(file => {
              const isSelected = selectedFile.filename === file.filename;
              return (
                <button
                  key={file.filename}
                  onClick={() => setSelectedFile(file)}
                  className={`flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-left text-xs transition-all shrink-0 md:shrink ${
                    isSelected
                      ? 'bg-[#006c49] text-white font-semibold shadow-xs'
                      : 'text-[#d9e3f6]/80 hover:bg-[#1a2638] hover:text-white'
                  }`}
                >
                  <FileCode className={`w-4 h-4 shrink-0 ${isSelected ? 'text-[#4edea3]' : 'text-[#6c7a71]'}`} />
                  <div className="truncate">
                    <div className="font-mono text-xs">{file.filename}</div>
                    <span className="text-[10px] opacity-75 hidden md:block">{file.category}</span>
                  </div>
                </button>
              );
            })}

            {/* Quick Tip Card in Sidebar */}
            <div className="mt-auto hidden md:block p-3 rounded-2xl bg-[#1a2638] border border-[#27313f] text-[11px] text-[#d9e3f6]/80 space-y-1.5">
              <div className="flex items-center gap-1.5 font-bold text-[#4edea3]">
                <Info className="w-3.5 h-3.5" />
                <span>Petunjuk Integrasi</span>
              </div>
              <p className="leading-relaxed">
                Buat project baru di Android Studio dengan template <em>Empty Activity (Compose)</em>, lalu salin file ini ke package <code className="text-[#4edea3]">com.inofarm.jogja</code>.
              </p>
            </div>
          </div>

          {/* Code Viewer Panel */}
          <div className="flex-1 flex flex-col bg-[#0b131e] overflow-hidden">
            {/* File info bar & Actions */}
            <div className="px-5 py-3 border-b border-[#27313f] flex items-center justify-between bg-[#121c2a]">
              <div>
                <span className="font-mono text-xs font-bold text-[#4edea3]">
                  {selectedFile.filename}
                </span>
                <p className="text-[11px] text-[#d9e3f6]/60 line-clamp-1">
                  {selectedFile.description}
                </p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={handleDownload}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#1a2638] hover:bg-[#27313f] text-[#d9e3f6] transition-colors border border-[#27313f]"
                  title="Unduh file ini"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span className="hidden sm:inline">Unduh .kt</span>
                </button>

                <button
                  onClick={handleCopy}
                  className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold bg-[#10b981] hover:bg-[#006c49] text-[#00422b] hover:text-white transition-all shadow-xs cursor-pointer active:scale-95"
                >
                  {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copied ? 'Tersalin!' : 'Salin Kode'}</span>
                </button>
              </div>
            </div>

            {/* Code Pre/Code block with line numbers */}
            <div className="flex-1 overflow-auto p-4 font-mono text-xs leading-relaxed text-[#d9e3f6]">
              <pre className="whitespace-pre">
                <code>{selectedFile.code}</code>
              </pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
