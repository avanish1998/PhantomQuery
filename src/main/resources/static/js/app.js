// Remove ES6 imports and use global React object
function ChatApp() {
    const [messages, setMessages] = React.useState([]);
    const [inputValue, setInputValue] = React.useState('');
    const [conversations, setConversations] = React.useState([]);
    const [currentConversationId, setCurrentConversationId] = React.useState(null);
    const textareaRef = React.useRef(null);
    const chatContainerRef = React.useRef(null);
    const wsRef = React.useRef(null);
    const [lastReceivedMessage, setLastReceivedMessage] = React.useState(null);
    const [clientId, setClientId] = React.useState(null);

    // Load conversations and set up WebSocket on component mount
    React.useEffect(() => {
        loadConversations();
        
        // Set up WebSocket connection
        console.log('Setting up WebSocket connection...');
        wsRef.current = new WebSocket(`ws://${window.location.host}/simple-websocket`);
        
        wsRef.current.onopen = () => {
            console.log('WebSocket connection established');
            
            // Send a connection message to the server
            const connectionMessage = {
                type: 'connection',
                message: 'React client connected'
            };
            wsRef.current.send(JSON.stringify(connectionMessage));
            console.log('Sent connection message to server');
        };
        
        wsRef.current.onmessage = (event) => {
            console.log(`Raw WebSocket message received: ${event.data}`);
            
            try {
                const data = JSON.parse(event.data);
                console.log(`Parsed WebSocket message: ${JSON.stringify(data)}`);
                
                // Update the last received message for UI display
                setLastReceivedMessage({
                    type: data.type,
                    content: data.type === 'transcription' ? data.text : 
                            data.type === 'ai_response' ? data.content : 
                            JSON.stringify(data),
                    timestamp: new Date()
                });
                
                if (data.type === 'transcription' && data.text) {
                    console.log(`Got transcription: "${data.text}"`);
                    
                    // Update React state
                    setInputValue(data.text);
                    
                    // Update textarea directly
                    if (textareaRef.current) {
                        textareaRef.current.value = data.text;
                        textareaRef.current.style.height = 'auto';
                        textareaRef.current.style.height = (textareaRef.current.scrollHeight) + 'px';
                    }
                } else if (data.type === 'ai_response') {
                    setMessages(prev => [...prev, {
                        role: 'assistant',
                        content: data.content,
                        timestamp: new Date()
                    }]);
                } else if (data.type === 'input_cleared') {
                    setInputValue('');
                    if (textareaRef.current) {
                        textareaRef.current.value = '';
                        textareaRef.current.style.height = 'auto';
                    }
                } else if (data.type === 'client_id') {
                    // Store the client ID received from the server
                    setClientId(data.clientId);
                    console.log(`Received client ID from server: ${data.clientId}`);
                }
            } catch (error) {
                console.error(`Error handling WebSocket message: ${error.message}`);
            }
        };
        
        wsRef.current.onerror = (error) => {
            console.error(`WebSocket error: ${error.message}`);
        };
        
        wsRef.current.onclose = () => {
            console.log('WebSocket connection closed');
        };
        
        // Clean up WebSocket connection on component unmount
        return () => {
            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, []);

    // Log when inputValue changes
    React.useEffect(() => {
        console.log(`inputValue changed to: "${inputValue}"`);
    }, [inputValue]);

    // Update textarea height when input value changes
    React.useEffect(() => {
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
            textareaRef.current.style.height = (textareaRef.current.scrollHeight) + 'px';
            console.log(`Textarea height updated to: ${textareaRef.current.scrollHeight}px`);
        }
    }, [inputValue]);

    // Scroll to bottom when messages change
    React.useEffect(() => {
        if (chatContainerRef.current) {
            chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
        }
    }, [messages]);

    const loadConversations = async () => {
        try {
            const response = await fetch('/api/conversations');
            const data = await response.json();
            setConversations(data);
            if (data.length > 0 && !currentConversationId) {
                setCurrentConversationId(data[0].id);
                loadMessages(data[0].id);
            }
        } catch (error) {
            console.error('Error loading conversations:', error);
        }
    };

    const loadMessages = async (conversationId) => {
        try {
            const response = await fetch(`/api/conversations/${conversationId}/messages`);
            const data = await response.json();
            setMessages(data.map(msg => ({
                ...msg,
                timestamp: new Date(msg.timestamp)
            })));
        } catch (error) {
            console.error('Error loading messages:', error);
        }
    };

    const createNewChat = async () => {
        try {
            const response = await fetch('/api/conversations', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ title: 'New Chat' })
            });
            const conversation = await response.json();
            setCurrentConversationId(conversation.id);
            setMessages([]);
            loadConversations();
        } catch (error) {
            console.error('Error creating new chat:', error);
        }
    };

    const sendMessage = () => {
        if (!inputValue.trim()) return;

        const newMessage = {
            id: Date.now(),
            role: 'user',
            content: inputValue,
            timestamp: new Date()
        };

        setMessages(prev => [...prev, newMessage]);
        setInputValue('');

        // Send message through WebSocket
        if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
            const message = {
                type: 'send_message',
                content: inputValue,
                conversationId: currentConversationId
            };
            wsRef.current.send(JSON.stringify(message));
            console.log('Sent message to server: ' + JSON.stringify(message));
        } else {
            console.error('WebSocket not connected, cannot send message');
        }
    };

    const clearInput = () => {
        setInputValue('');
        const textarea = document.querySelector('.input-box textarea');
        if (textarea) {
            textarea.value = '';
            textarea.style.height = 'auto';
        }
    };

    const handleKeyDown = (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            sendMessage();
        }
    };

    const autoResize = (textarea) => {
        textarea.style.height = 'auto';
        textarea.style.height = (textarea.scrollHeight) + 'px';
    };

    const formatMessageContent = (content) => {
        // First, handle code blocks with language specification
        const parts = content.split(/(```[\s\S]*?```)/g);
        
        return parts.map((part, index) => {
            if (part.startsWith('```') && part.endsWith('```')) {
                // Extract language and code from code block
                const codeBlock = part.slice(3, -3);
                const firstLine = codeBlock.split('\n')[0];
                const language = firstLine.trim();
                const code = language ? codeBlock.slice(firstLine.length + 1).trim() : codeBlock.trim();
                
                return (
                    <pre key={index} className="code-block">
                        <code className={language ? `language-${language}` : ''}>
                            {code}
                        </code>
                    </pre>
                );
            }
            
            // Process inline formatting
            let formattedText = part;
            
            // Handle bold text (both ** and __ formats)
            formattedText = formattedText.replace(/(\*\*|__)(.*?)\1/g, '<strong>$2</strong>');
            
            // Handle italic text (both * and _ formats)
            formattedText = formattedText.replace(/(\*|_)(.*?)\1/g, '<em>$2</em>');
            
            // Handle inline code
            formattedText = formattedText.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');
            
            // Handle strikethrough
            formattedText = formattedText.replace(/~~(.*?)~~/g, '<del>$1</del>');
            
            // Handle blockquotes
            formattedText = formattedText.replace(/^> (.*?)$/gm, '<blockquote>$1</blockquote>');
            
            // Handle unordered lists
            formattedText = formattedText.replace(/^\s*[-*+]\s+(.*?)$/gm, '<li>$1</li>');
            formattedText = formattedText.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
            
            // Handle ordered lists
            formattedText = formattedText.replace(/^\s*\d+\.\s+(.*?)$/gm, '<li>$1</li>');
            formattedText = formattedText.replace(/(<li>.*<\/li>)/s, '<ul class="ordered-list">$1</ul>');
            
            // Handle horizontal rules
            formattedText = formattedText.replace(/^---$/gm, '<hr />');
            
            // Handle links
            formattedText = formattedText.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
            
            // Split by newlines and wrap in paragraphs
            const paragraphs = formattedText.split('\n\n');
            
            return paragraphs.map((paragraph, pIndex) => {
                // Check if the paragraph is already wrapped in HTML tags
                if (paragraph.match(/<[^>]+>/)) {
                    return <div key={`${index}-${pIndex}`} dangerouslySetInnerHTML={{ __html: paragraph }} />;
                }
                
                // Otherwise, wrap in a paragraph tag
                return <p key={`${index}-${pIndex}`}>{paragraph}</p>;
            });
        });
    };

    return (
        <div className="container">
            <div className="sidebar">
                <button className="new-chat-btn" onClick={createNewChat}>
                    + New chat
                </button>
                <div className="conversation-list">
                    {conversations.map(conv => (
                        <div
                            key={conv.id}
                            className={`conversation-item ${conv.id === currentConversationId ? 'active' : ''}`}
                            onClick={() => {
                                setCurrentConversationId(conv.id);
                                loadMessages(conv.id);
                            }}
                        >
                            {conv.title}
                        </div>
                    ))}
                </div>
            </div>
            <div className="main-content">
                <div className="chat-container" ref={chatContainerRef}>
                    {messages.map((message, index) => (
                        <div key={index} className={`message-wrapper ${message.role}`}>
                            <div className={`message ${message.role}`}>
                                {formatMessageContent(message.content)}
                            </div>
                            <div className="message-time">
                                {message.timestamp.toLocaleTimeString()}
                            </div>
                        </div>
                    ))}
                </div>
                <div className="input-container">
                    <div className="input-box">
                        <textarea
                            ref={textareaRef}
                            value={inputValue}
                            onChange={(e) => {
                                console.log(`Textarea onChange: "${e.target.value}"`);
                                setInputValue(e.target.value);
                                autoResize(e.target);
                            }}
                            onKeyDown={handleKeyDown}
                            placeholder="System audio transcription will appear here..."
                            rows="1"
                            style={{ minHeight: '40px', maxHeight: '200px' }}
                        />
                        <button className="button" onClick={sendMessage}>
                            Send
                        </button>
                        <button className="button" onClick={clearInput}>
                            Clear
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

// Add ReactDOM render call at the end of the file
ReactDOM.render(<ChatApp />, document.getElementById('root'));