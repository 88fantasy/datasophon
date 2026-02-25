import React, { useState, useEffect } from 'react';

const App = () => {
    const [activeNode, setActiveNode] = useState('Full Stack');
    const [aiAnalysis, setAiAnalysis] = useState('点击节点以分析该模块在 VOS 系统中的运作状态...');
    const [loading, setLoading] = useState(false);
    const [isPanelVisible, setIsPanelVisible] = useState(false);

    // 模拟 API Key 环境
    const apiKey = "";

    useEffect(() => {
        const timer = setTimeout(() => setIsPanelVisible(true), 1000);
        return () => clearTimeout(timer);
    }, []);

    //   const fetchWithRetry = async (url, options, retries = 5, backoff = 1000) => {
    //     for (let i = 0; i < retries; i++) {
    //       try {
    //         const response = await fetch(url, options);
    //         if (response.ok) return response;
    //       } catch (err) {}
    //       await new Promise(resolve => setTimeout(resolve, backoff));
    //       backoff *= 2;
    //     }
    //     throw new Error("Failed after retries");
    //   };

    const analyze = async (target) => {
        // setActiveNode(target);
        // setLoading(true);
        // setAiAnalysis(`正在深度分析 ${target} 的链路状态...`);

        // const systemPrompt = "你是一个专业的云原生运维专家。请简要分析 VOS 系统中该组件的作用及优化方向。语言简洁，富有科技感。";
        // const userQuery = `分析 VOS 运维拓扑中 "${target}" 的角色。拓扑结构为：ARTIFACTS -> CONTAINERS -> KUBERNETES -> SERVICES，最终汇聚于中心运维核心。请给出 3 条专业洞察。`;

        // try {
        //   const response = await fetchWithRetry(
        //     `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-09-2025:generateContent?key=${apiKey}`,
        //     {
        //       method: 'POST',
        //       headers: { 'Content-Type': 'application/json' },
        //       body: JSON.stringify({
        //         contents: [{ parts: [{ text: userQuery }] }],
        //         systemInstruction: { parts: [{ text: systemPrompt }] }
        //       })
        //     }
        //   );

        //   const data = await response.json();
        //   const text = data.candidates?.[0]?.content?.parts?.[0]?.text || "分析暂时不可用。";
        //   setAiAnalysis(text);
        // } catch (error) {
        //   setAiAnalysis("无法连接到 AI 服务。请检查网络或 API 配置。");
        // } finally {
        //   setLoading(false);
        // }
    };

    return (
        <div className="flex-1 flex flex-col items-center justify-center min-h-screen  bg-slate-50 p-4 font-sans overflow-hidden">
            <div className="relative w-full max-w-2xl aspect-square flex items-center justify-center">
                <svg viewBox="0 0 400 400" className="w-full h-full">
                    <defs>
                        <linearGradient id="mainGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                            <stop offset="0%" stopColor="#3b82f6" />
                            <stop offset="100%" stopColor="#1e40af" />
                        </linearGradient>

                        <style>
                            {`
                @keyframes flow {
                  from { stroke-dashoffset: 20; }
                  to { stroke-dashoffset: 0; }
                }
                .flow-line {
                  stroke-dasharray: 8, 12;
                  animation: flow 1.5s linear infinite;
                }
                @keyframes center-pulse {
                  0%, 100% { transform: scale(1); opacity: 1; }
                  50% { transform: scale(1.08); opacity: 0.9; }
                }
                .center-group {
                  animation: center-pulse 4s ease-in-out infinite;
                  transform-origin: center;
                }
                @keyframes rotate-slow {
                  from { transform: rotate(0deg); }
                  to { transform: rotate(360deg); }
                }
                @keyframes rotate-reverse {
                  from { transform: rotate(360deg); }
                  to { transform: rotate(0deg); }
                }
                .orbit-slow {
                  animation: rotate-slow 60s linear infinite;
                  transform-origin: center;
                }
                .orbit-reverse {
                  animation: rotate-reverse 40s linear infinite;
                  transform-origin: center;
                }
              `}
                        </style>
                    </defs>

                    {/* 背景轨道与圆环 */}
                    <g className="orbit-slow">
                        <circle cx="200" cy="200" r="170" fill="none" stroke="#e2e8f0" strokeWidth="1" strokeDasharray="4,4" />
                        <circle cx="30" cy="200" r="3" fill="#cbd5e1" />
                    </g>
                    <g className="orbit-reverse">
                        <circle cx="200" cy="200" r="130" fill="none" stroke="#f1f5f9" strokeWidth="1.5" strokeDasharray="10,10" />
                        <circle cx="330" cy="200" r="2" fill="#94a3b8" opacity="0.5" />
                    </g>
                    <circle cx="200" cy="200" r="100" fill="none" stroke="#f8fafc" strokeWidth="1" />

                    {/* 连接线 */}
                    <path d="M 200 75 L 200 155" fill="none" stroke="#3b82f6" strokeWidth="2.5" strokeLinecap="round" className="flow-line" />
                    <path d="M 325 200 L 245 200" fill="none" stroke="#10b981" strokeWidth="2.5" strokeLinecap="round" className="flow-line" />
                    <path d="M 200 325 L 200 245" fill="none" stroke="#6366f1" strokeWidth="2.5" strokeLinecap="round" className="flow-line" />
                    <path d="M 75 200 L 155 200" fill="none" stroke="#f59e0b" strokeWidth="2.5" strokeLinecap="round" className="flow-line" />

                    {/* KUBERNETES */}
                    <g className="cursor-pointer group" onClick={() => analyze('KUBERNETES')}>
                        <g transform="translate(182, 42) scale(0.9)" stroke="#3b82f6" strokeWidth="2.2" fill="none" className="transition-transform duration-300">
                            <circle cx="20" cy="20" r="10" />
                            <path d="M20 2v8M20 30v8M4 14l6 3M30 23l6 3M7 32l5-5M28 13l5-5M35 15l-7 4" strokeLinecap="round" />
                            <circle cx="20" cy="20" r="4" fill="#3b82f6" fillOpacity="0.3" />
                        </g>
                        <text x="200" y="30" textAnchor="middle" className="fill-slate-500 font-bold text-[10px] tracking-[1.5px]">KUBERNETES</text>
                    </g>

                    {/* SERVICES */}
                    <g className="cursor-pointer group" onClick={() => analyze('SERVICES')}>
                        <g transform="translate(325, 185) scale(0.9)" fill="none" stroke="#10b981" strokeWidth="2.5" className="transition-transform duration-300">
                            <rect x="2" y="2" width="12" height="12" rx="2" />
                            <rect x="16" y="2" width="12" height="12" rx="2" fill="#10b981" fillOpacity="0.2" />
                            <rect x="2" y="16" width="12" height="12" rx="2" />
                            <rect x="16" y="16" width="12" height="12" rx="2" strokeDasharray="3,3" />
                        </g>
                        <text x="342" y="245" textAnchor="middle" className="fill-slate-500 font-bold text-[10px] tracking-[1.5px]">SERVICES</text>
                    </g>

                    {/* CONTAINERS */}
                    <g className="cursor-pointer group" onClick={() => analyze('CONTAINERS')}>
                        <g transform="translate(180, 325) scale(1)" fill="none" stroke="#6366f1" strokeWidth="2.2" className="transition-transform duration-300">
                            <rect x="4" y="14" width="24" height="12" rx="1" />
                            <path d="M10 14v12M16 14v12M22 14v12" />
                            <rect x="10" y="4" width="20" height="10" rx="1" fill="#6366f1" fillOpacity="0.1" />
                            <path d="M15 4v10M20 4v10M25 4v10" />
                        </g>
                        <text x="200" y="380" text-anchor="middle" className="fill-slate-500 font-bold text-[10px] tracking-[1.5px]">CONTAINERS</text>
                    </g>

                    {/* ARTIFACTS */}
                    <g className="cursor-pointer group" onClick={() => analyze('ARTIFACTS')}>
                        <g transform="translate(45, 182) scale(0.9)" fill="none" stroke="#f59e0b" strokeWidth="2.5" className="transition-transform duration-300">
                            <path d="M4 10l16-8 16 8v16l-16 8-16-8z" strokeLinejoin="round" />
                            <path d="M4 10l16 8 16-8M20 18v16" strokeLinecap="round" />
                        </g>
                        <text x="60" y="245" text-anchor="middle" className="fill-slate-500 font-bold text-[10px] tracking-[1.5px]">ARTIFACTS</text>
                    </g>

                    {/* CENTER CORE */}
                    <g className="center-group cursor-pointer" onClick={() => analyze('VOS Core')}>
                        <g transform="translate(175, 175) scale(1.25)" fill="none" stroke="url(#mainGrad)" strokeWidth="2.5">
                            <path d="M8 28 A13 13 0 1 1 32 28" strokeWidth="2" />
                            <circle cx="20" cy="20" r="16" strokeDasharray="2,6" opacity="0.3" />
                            <path d="M20 20 L26 12" strokeLinecap="round" strokeWidth="3" />
                            <circle cx="20" cy="20" r="4" fill="url(#mainGrad)" />
                            <circle cx="12" cy="32" r="1.5" fill="url(#mainGrad)" />
                            <circle cx="20" cy="34" r="1.5" fill="url(#mainGrad)" />
                            <circle cx="28" cy="32" r="1.5" fill="url(#mainGrad)" />
                        </g>
                    </g>
                </svg>
            </div>
        </div>
    );
};

export default App;